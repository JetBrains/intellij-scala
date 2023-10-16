package org.jetbrains.plugins.scala.lang.dfa.controlFlow.transform

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

trait ExpressionTransformation { this: ScalaDfaControlFlowBuilder =>
  def transformExpression(element: ScExpression): Unit = element match {
    case someExpression if isUnsupportedPureExpressionType(someExpression) => pushUnknownValue()
    case someExpression if isUnsupportedImpureExpressionType(someExpression) => pushUnknownCall(someExpression, 0)
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
    case _: ScUnitExpr => pushUnit()
    case _: ScTemplateDefinition => pushUnknownValue()
    case _ => throw TransformationFailedException(element, "Unsupported expression.")
  }

  def transformExpression(element: Option[ScExpression]): Unit = element match {
    case Some(element) => transformExpression(element)
    case None => pushUnknownValue()
  }

  private def isUnsupportedPureExpressionType(expression: ScExpression): Boolean = {
    expression.is[ScTuple, ScThisReference, ScSuperReference]
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
      pushUnit()
    } else {
      statements.init.foreach { statement =>
        transformPsiElement(statement)
        pop()
      }

      transformPsiElement(statements.last)
      addInstruction(new FinishElementInstruction(block))
    }
  }

  private def transformParenthesisedExpression(parenthesised: ScParenthesisedExpr): Unit = {
    transformExpression(parenthesised.innerElement)
  }

  private def transformLiteral(literal: ScLiteral): Unit = {
    if (literal.is[ScInterpolatedStringLiteral]) pushUnknownCall(literal, 0)
    else addInstruction(new PushValueInstruction(literalToDfType(literal), ScalaStatementAnchor(literal)))
  }

  private def transformIfExpression(ifExpression: ScIf): Unit = {
    val returnType = ifExpression.`type`().getOrAny
    val skipThenOffset = new DeferredOffset
    val skipElseOffset = new DeferredOffset

    transformExpression(ifExpression.condition)
    addInstruction(new ConditionalGotoInstruction(skipThenOffset, DfTypes.FALSE, ifExpression.condition.orNull))

    addInstruction(new FinishElementInstruction(null))
    transformExpression(ifExpression.thenExpression)
    buildImplicitConversion(ifExpression.thenExpression, Some(returnType))
    addInstruction(new GotoInstruction(skipElseOffset))
    setOffset(skipThenOffset)

    addInstruction(new FinishElementInstruction(null))
    transformExpression(ifExpression.elseExpression)
    ifExpression.elseExpression.foreach(expression => buildImplicitConversion(Some(expression), Some(returnType)))
    setOffset(skipElseOffset)

    addInstruction(new FinishElementInstruction(ifExpression))
  }

  private def transformReference(expression: ScReferenceExpression): Unit = {
    if (isReferenceExpressionInvocation(expression)) {
      transformInvocation(expression)
    } else {
      expression.qualifier.foreach { qualifier =>
        transformExpression(qualifier)
        pop()
      }

      val expectedType = resolveExpressionType(expression)
      ScalaDfaVariableDescriptor.fromReferenceExpression(expression) match {
        case Some(descriptor) => pushVariable(descriptor, expression)
          buildImplicitConversion(Some(expression), Some(expectedType))
        case _ => pushUnknownCall(expression, 0)
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
          pushUnknownValue()
        case _ => pushUnknownCall(assignment, 0)
      }
      case _ => pushUnknownCall(assignment, 0)
    }
  }

  private def transformDoWhileLoop(doWhileLoop: ScDo): Unit = {
    // TODO implement transformation
    pushUnknownCall(doWhileLoop, 0)
  }

  private def transformWhileLoop(whileLoop: ScWhile): Unit = {
    // TODO implement transformation
    pushUnknownCall(whileLoop, 0)
  }

  private def transformForExpression(forExpression: ScFor): Unit = {
    forExpression.desugared() match {
      case Some(desugared) => transformExpression(desugared)
      case _ => pushUnknownCall(forExpression, 0)
    }
  }

  private def transformMatchExpression(matchExpression: ScMatch): Unit = {
    // TODO implement transformation
    pushUnknownCall(matchExpression, 0)
  }

  //noinspection UnstableApiUsage
  private def transformThrowStatement(throwStatement: ScThrow): Unit = {
    val exceptionExpression = throwStatement.expression
    exceptionExpression match {
      case Some(exception) => transformExpression(exception)
        pop()
        val psiType = exception.`type`().getOrAny.toPsiType
        val transfer = new ExceptionTransfer(TypeConstraints.instanceOf(psiType))
        addInstruction(new ThrowInstruction(transferValue(transfer), exception))
        pushUnknownValue()
      case _ => pushUnknownCall(throwStatement, 0)
    }
  }

  private def transformReturnStatement(returnStatement: ScReturn): Unit = {
    returnStatement.expr match {
      case Some(expression) => transformExpression(expression)
      case _ => pushUnknownValue()
    }

    addReturnInstruction(returnStatement.expr)
  }
}
