package org.jetbrains.plugins.scala.lang.dfa.controlFlow.transformations

import com.intellij.codeInspection.dataFlow.TypeConstraints
import com.intellij.codeInspection.dataFlow.java.inst.ThrowInstruction
import com.intellij.codeInspection.dataFlow.jvm.transfer.ExceptionTransfer
import com.intellij.codeInspection.dataFlow.lang.ir.ControlFlow.DeferredOffset
import com.intellij.codeInspection.dataFlow.lang.ir._
import com.intellij.codeInspection.dataFlow.types.DfTypes
import com.intellij.psi.PsiMethod
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.dfa.analysis.framework.ScalaStatementAnchor
import org.jetbrains.plugins.scala.lang.dfa.controlFlow.{ScalaDfaControlFlowBuilder, ScalaDfaVariableDescriptor, TransformationFailedException}
import org.jetbrains.plugins.scala.lang.dfa.utils.ScalaDfaTypeUtils.{literalToDfType, resolveExpressionType}
import org.jetbrains.plugins.scala.lang.psi.api.base.{ScInterpolatedStringLiteral, ScLiteral}
import org.jetbrains.plugins.scala.lang.psi.api.expr._
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScTemplateDefinition
import org.jetbrains.plugins.scala.util.SAMUtil.isFunctionalExpression

class ExpressionTransformer(val wrappedExpression: ScExpression)
  extends ScalaPsiElementTransformer(wrappedExpression) {

  override def toString: String = s"ExpressionTransformer: $wrappedExpression"

  override def transform(builder: ScalaDfaControlFlowBuilder): Unit = wrappedExpression match {
    case someExpression if isUnsupportedPureExpressionType(someExpression) => builder.pushUnknownValue()
    case someExpression if isUnsupportedImpureExpressionType(someExpression) => builder.pushUnknownCall(someExpression, 0)
    case block: ScBlockExpr => transformBlock(block, builder)
    case parenthesised: ScParenthesisedExpr => transformParenthesisedExpression(parenthesised, builder)
    case invocation: MethodInvocation => transformInvocation(invocation, builder)
    case literal: ScLiteral => transformLiteral(literal, builder)
    case ifExpression: ScIf => transformIfExpression(ifExpression, builder)
    case reference: ScReferenceExpression => transformReference(reference, builder)
    case typedExpression: ScTypedExpression => transformTypedExpression(typedExpression, builder)
    case newTemplateDefinition: ScNewTemplateDefinition => transformNewTemplateDefinition(newTemplateDefinition, builder)
    case assignment: ScAssignment => transformAssignment(assignment, builder)
    case doWhileLoop: ScDo => transformDoWhileLoop(doWhileLoop, builder)
    case whileLoop: ScWhile => transformWhileLoop(whileLoop, builder)
    case forExpression: ScFor => transformForExpression(forExpression, builder)
    case matchExpression: ScMatch => transformMatchExpression(matchExpression, builder)
    case throwStatement: ScThrow => transformThrowStatement(throwStatement, builder)
    case returnStatement: ScReturn => transformReturnStatement(returnStatement, builder)
    case _: ScTemplateDefinition => builder.pushUnknownValue()
    case _ => throw TransformationFailedException(wrappedExpression, "Unsupported expression.")
  }

  protected def transformExpression(expression: ScExpression, builder: ScalaDfaControlFlowBuilder): Unit = {
    new ExpressionTransformer(expression).transform(builder)
  }

  private def isUnsupportedPureExpressionType(expression: ScExpression): Boolean = {
    expression.is[ScUnitExpr, ScTuple, ScThisReference, ScSuperReference]
  }

  private def isUnsupportedImpureExpressionType(expression: ScExpression): Boolean = {
    isFunctionalExpression(expression) || expression.is[ScTry, ScUnderscoreSection, ScGenericCall]
  }

  private def isReferenceExpressionInvocation(expression: ScReferenceExpression): Boolean = {
    expression.bind().map(_.element).exists(_.is[PsiMethod])
  }

  private def transformBlock(block: ScBlockExpr, builder: ScalaDfaControlFlowBuilder): Unit = {
    val statements = block.statements
    if (statements.isEmpty) {
      builder.pushUnknownValue()
    } else {
      statements.init.foreach { statement =>
        new ScalaPsiElementTransformer(statement).transform(builder)
        builder.popReturnValue()
      }

      transformPsiElement(statements.last, builder)
      builder.addInstruction(new FinishElementInstruction(block))
    }
  }

  private def transformParenthesisedExpression(parenthesised: ScParenthesisedExpr, builder: ScalaDfaControlFlowBuilder): Unit = {
    parenthesised.innerElement.foreach(transformExpression(_, builder))
  }

  private def transformLiteral(literal: ScLiteral, builder: ScalaDfaControlFlowBuilder): Unit = {
    if (literal.is[ScInterpolatedStringLiteral]) builder.pushUnknownCall(literal, 0)
    else builder.addInstruction(new PushValueInstruction(literalToDfType(literal), ScalaStatementAnchor(literal)))
  }

  private def transformIfExpression(ifExpression: ScIf, builder: ScalaDfaControlFlowBuilder): Unit = {
    val returnType = ifExpression.`type`().getOrAny
    val skipThenOffset = new DeferredOffset
    val skipElseOffset = new DeferredOffset

    transformIfPresent(ifExpression.condition, builder)
    builder.addInstruction(new ConditionalGotoInstruction(skipThenOffset, DfTypes.FALSE, ifExpression.condition.orNull))

    builder.addInstruction(new FinishElementInstruction(null))
    transformIfPresent(ifExpression.thenExpression, builder)
    builder.addImplicitConversion(ifExpression.thenExpression, Some(returnType))
    builder.addInstruction(new GotoInstruction(skipElseOffset))
    builder.setOffset(skipThenOffset)

    builder.addInstruction(new FinishElementInstruction(null))
    transformIfPresent(ifExpression.elseExpression, builder)
    ifExpression.elseExpression.foreach(expression => builder.addImplicitConversion(Some(expression), Some(returnType)))
    builder.setOffset(skipElseOffset)

    builder.addInstruction(new FinishElementInstruction(ifExpression))
  }

  private def transformReference(expression: ScReferenceExpression, builder: ScalaDfaControlFlowBuilder): Unit = {
    if (isReferenceExpressionInvocation(expression)) {
      new InvocationTransformer(expression).transform(builder)
    } else {
      expression.qualifier.foreach { qualifier =>
        transformExpression(qualifier, builder)
        builder.popReturnValue()
      }

      val expectedType = resolveExpressionType(expression)
      ScalaDfaVariableDescriptor.fromReferenceExpression(expression) match {
        case Some(descriptor) => builder.pushVariable(descriptor, expression)
          builder.addImplicitConversion(Some(expression), Some(expectedType))
        case _ => builder.pushUnknownCall(expression, 0)
      }
    }
  }

  private def transformInvocation(invocationExpression: ScExpression, builder: ScalaDfaControlFlowBuilder): Unit = {
    new InvocationTransformer(invocationExpression).transform(builder)
  }

  private def transformTypedExpression(typedExpression: ScTypedExpression, builder: ScalaDfaControlFlowBuilder): Unit = {
    transformExpression(typedExpression.expr, builder)
  }

  private def transformNewTemplateDefinition(newTemplateDefinition: ScNewTemplateDefinition,
                                             builder: ScalaDfaControlFlowBuilder): Unit = {
    new InvocationTransformer(newTemplateDefinition).transform(builder)
  }

  private def transformAssignment(assignment: ScAssignment, builder: ScalaDfaControlFlowBuilder): Unit = {
    assignment.leftExpression match {
      case reference: ScReferenceExpression => ScalaDfaVariableDescriptor.fromReferenceExpression(reference) match {
        case Some(descriptor) => val definedType = resolveExpressionType(assignment.leftExpression)
          builder.assignVariableValue(descriptor, assignment.rightExpression, definedType)
          builder.pushUnknownValue()
        case _ => builder.pushUnknownCall(assignment, 0)
      }
      case _ => builder.pushUnknownCall(assignment, 0)
    }
  }

  private def transformDoWhileLoop(doWhileLoop: ScDo, builder: ScalaDfaControlFlowBuilder): Unit = {
    // TODO implement transformation
    builder.pushUnknownCall(doWhileLoop, 0)
  }

  private def transformWhileLoop(whileLoop: ScWhile, builder: ScalaDfaControlFlowBuilder): Unit = {
    // TODO implement transformation
    builder.pushUnknownCall(whileLoop, 0)
  }

  private def transformForExpression(forExpression: ScFor, builder: ScalaDfaControlFlowBuilder): Unit = {
    forExpression.desugared() match {
      case Some(desugared) => transformExpression(desugared, builder)
      case _ => builder.pushUnknownCall(forExpression, 0)
    }
  }

  private def transformMatchExpression(matchExpression: ScMatch, builder: ScalaDfaControlFlowBuilder): Unit = {
    // TODO implement transformation
    builder.pushUnknownCall(matchExpression, 0)
  }

  //noinspection UnstableApiUsage
  private def transformThrowStatement(throwStatement: ScThrow, builder: ScalaDfaControlFlowBuilder): Unit = {
    val exceptionExpression = throwStatement.expression
    exceptionExpression match {
      case Some(exception) => transformExpression(exception, builder)
        builder.popReturnValue()
        val psiType = exception.`type`().getOrAny.toPsiType
        val transfer = new ExceptionTransfer(TypeConstraints.instanceOf(psiType))
        builder.addInstruction(new ThrowInstruction(builder.transferValue(transfer), exception))
        builder.pushUnknownValue()
      case _ => builder.pushUnknownCall(throwStatement, 0)
    }
  }

  private def transformReturnStatement(returnStatement: ScReturn, builder: ScalaDfaControlFlowBuilder): Unit = {
    returnStatement.expr match {
      case Some(expression) => transformExpression(expression, builder)
      case _ => builder.pushUnknownValue()
    }

    builder.addReturnInstruction(returnStatement.expr)
  }
}
