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

trait ExpressionTransformer extends Transformer { this: ScalaPsiElementTransformer =>
  def transformExpression(element: ScExpression): Unit = element match {
    case someExpression if isUnsupportedPureExpressionType(someExpression) => builder.pushUnknownValue()
    case someExpression if isUnsupportedImpureExpressionType(someExpression) => builder.pushUnknownCall(someExpression, 0)
    case block: ScBlockExpr => transformBlock(block)
    case parenthesised: ScParenthesisedExpr => transformParenthesisedExpression(parenthesised)
    case invocation: MethodInvocation => transformInvocation(invocation)
    case literal: ScLiteral => transformLiteral(literal)
    case ifExpression: ScIf => transformIfExpression(ifExpression)
    case reference: ScReferenceExpression => transformReference(reference)
    case typedExpression: ScTypedExpression => transformTypedExpression(typedExpression)
    case newTemplateDefinition: ScNewTemplateDefinition => transformNewTemplateDefinition(newTemplateDefinition)
    case assignment: ScAssignment => transformAssignment(assignment)
    case doWhileLoop: ScDo => transformDoWhileLoop(doWhileLoop)
    case whileLoop: ScWhile => transformWhileLoop(whileLoop)
    case forExpression: ScFor => transformForExpression(forExpression)
    case matchExpression: ScMatch => transformMatchExpression(matchExpression)
    case throwStatement: ScThrow => transformThrowStatement(throwStatement)
    case returnStatement: ScReturn => transformReturnStatement(returnStatement)
    case _: ScTemplateDefinition => builder.pushUnknownValue()
    case _ => throw TransformationFailedException(element, "Unsupported expression.")
  }
  
  def transformExpression(element: Option[ScExpression]): Unit = element match {
    case Some(element) => transformExpression(element)
    case None => builder.pushUnknownValue()
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

  private def transformBlock(block: ScBlockExpr): Unit = {
    val statements = block.statements
    if (statements.isEmpty) {
      builder.pushUnknownValue()
    } else {
      statements.init.foreach { statement =>
        transformPsiElement(statement)
        builder.popReturnValue()
      }

      transformPsiElement(statements.last)
      builder.addInstruction(new FinishElementInstruction(block))
    }
  }

  private def transformParenthesisedExpression(parenthesised: ScParenthesisedExpr): Unit = {
    parenthesised.innerElement.foreach(transformExpression)
  }

  private def transformLiteral(literal: ScLiteral): Unit = {
    if (literal.is[ScInterpolatedStringLiteral]) builder.pushUnknownCall(literal, 0)
    else builder.addInstruction(new PushValueInstruction(literalToDfType(literal), ScalaStatementAnchor(literal)))
  }

  private def transformIfExpression(ifExpression: ScIf): Unit = {
    val returnType = ifExpression.`type`().getOrAny
    val skipThenOffset = new DeferredOffset
    val skipElseOffset = new DeferredOffset

    transformExpression(ifExpression.condition)
    builder.addInstruction(new ConditionalGotoInstruction(skipThenOffset, DfTypes.FALSE, ifExpression.condition.orNull))

    builder.addInstruction(new FinishElementInstruction(null))
    transformExpression(ifExpression.thenExpression)
    addImplicitConversion(ifExpression.thenExpression, Some(returnType))
    builder.addInstruction(new GotoInstruction(skipElseOffset))
    builder.setOffset(skipThenOffset)

    builder.addInstruction(new FinishElementInstruction(null))
    transformExpression(ifExpression.elseExpression)
    ifExpression.elseExpression.foreach(expression => addImplicitConversion(Some(expression), Some(returnType)))
    builder.setOffset(skipElseOffset)

    builder.addInstruction(new FinishElementInstruction(ifExpression))
  }

  private def transformReference(expression: ScReferenceExpression): Unit = {
    if (isReferenceExpressionInvocation(expression)) {
      transformInvocation(expression)
    } else {
      expression.qualifier.foreach { qualifier =>
        transformExpression(qualifier)
        builder.popReturnValue()
      }

      val expectedType = resolveExpressionType(expression)
      ScalaDfaVariableDescriptor.fromReferenceExpression(expression) match {
        case Some(descriptor) => builder.pushVariable(descriptor, expression)
          addImplicitConversion(Some(expression), Some(expectedType))
        case _ => builder.pushUnknownCall(expression, 0)
      }
    }
  }

  private def transformTypedExpression(typedExpression: ScTypedExpression): Unit = {
    transformExpression(typedExpression.expr)
  }

  private def transformNewTemplateDefinition(newTemplateDefinition: ScNewTemplateDefinition): Unit = {
    transformInvocation(newTemplateDefinition)
  }

  private def transformAssignment(assignment: ScAssignment): Unit = {
    assignment.leftExpression match {
      case reference: ScReferenceExpression => ScalaDfaVariableDescriptor.fromReferenceExpression(reference) match {
        case Some(descriptor) => val definedType = resolveExpressionType(assignment.leftExpression)
          assignVariableValue(descriptor, assignment.rightExpression, definedType)
          builder.pushUnknownValue()
        case _ => builder.pushUnknownCall(assignment, 0)
      }
      case _ => builder.pushUnknownCall(assignment, 0)
    }
  }

  private def transformDoWhileLoop(doWhileLoop: ScDo): Unit = {
    // TODO implement transformation
    builder.pushUnknownCall(doWhileLoop, 0)
  }

  private def transformWhileLoop(whileLoop: ScWhile): Unit = {
    // TODO implement transformation
    builder.pushUnknownCall(whileLoop, 0)
  }

  private def transformForExpression(forExpression: ScFor): Unit = {
    forExpression.desugared() match {
      case Some(desugared) => transformExpression(desugared)
      case _ => builder.pushUnknownCall(forExpression, 0)
    }
  }

  private def transformMatchExpression(matchExpression: ScMatch): Unit = {
    // TODO implement transformation
    builder.pushUnknownCall(matchExpression, 0)
  }

  //noinspection UnstableApiUsage
  private def transformThrowStatement(throwStatement: ScThrow): Unit = {
    val exceptionExpression = throwStatement.expression
    exceptionExpression match {
      case Some(exception) => transformExpression(exception)
        builder.popReturnValue()
        val psiType = exception.`type`().getOrAny.toPsiType
        val transfer = new ExceptionTransfer(TypeConstraints.instanceOf(psiType))
        builder.addInstruction(new ThrowInstruction(builder.transferValue(transfer), exception))
        builder.pushUnknownValue()
      case _ => builder.pushUnknownCall(throwStatement, 0)
    }
  }

  private def transformReturnStatement(returnStatement: ScReturn): Unit = {
    returnStatement.expr match {
      case Some(expression) => transformExpression(expression)
      case _ => builder.pushUnknownValue()
    }

    builder.addReturnInstruction(returnStatement.expr)
  }
}
