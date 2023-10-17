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
  def transformExpression(element: ScExpression, rreq: ResultReq): Unit = element match {
    case someExpression if isUnsupportedPureExpressionType(someExpression) => pushUnknownValue(rreq)
    case someExpression if isUnsupportedImpureExpressionType(someExpression) => buildUnknownCall(someExpression, 0, rreq)
    case block: ScBlockExpr => transformBlock(block, rreq)
    case parenthesised: ScParenthesisedExpr => transformParenthesisedExpression(parenthesised, rreq)
    case invocation: MethodInvocation => transformInvocation(invocation, rreq)
    case literal: ScLiteral => transformLiteral(literal, rreq)
    case ifExpression: ScIf => transformIfExpression(ifExpression, rreq)
    case reference: ScReferenceExpression => transformReference(reference, rreq)
    case typedExpression: ScTypedExpression => transformTypedExpression(typedExpression, rreq)
    case newTemplateDefinition: ScNewTemplateDefinition => transformNewTemplateDefinition(newTemplateDefinition, rreq)
    case assignment: ScAssignment => transformAssignment(assignment, rreq)
    case doWhileLoop: ScDo => transformDoWhileLoop(doWhileLoop, rreq)
    case whileLoop: ScWhile => transformWhileLoop(whileLoop, rreq)
    case forExpression: ScFor => transformForExpression(forExpression, rreq)
    case matchExpression: ScMatch => transformMatchExpression(matchExpression, rreq)
    case throwStatement: ScThrow => transformThrowStatement(throwStatement, rreq)
    case returnStatement: ScReturn => transformReturnStatement(returnStatement)
    case _: ScUnitExpr => pushUnit(rreq)
    case _: ScTemplateDefinition => pushUnknownValue(rreq)
    case _ => throw TransformationFailedException(element, "Unsupported expression.")
  }

  def transformExpression(element: Option[ScExpression], rreq: ResultReq): Unit = element match {
    case Some(element) => transformExpression(element, rreq)
    case None => pushUnknownValue(rreq)
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

  private def transformBlock(block: ScBlockExpr, rreq: ResultReq): Unit = {
    val statements = block.statements
    if (statements.isEmpty) {
      pushUnit(rreq)
    } else {
      statements.init.foreach { statement =>
        transformStatement(statement, ResultReq.None)
      }

      transformStatement(statements.last, rreq)
      addInstruction(new FinishElementInstruction(block))
    }
  }

  private def transformParenthesisedExpression(parenthesised: ScParenthesisedExpr, rreq: ResultReq): Unit = {
    transformExpression(parenthesised.innerElement, rreq)
  }

  private def transformLiteral(literal: ScLiteral, rreq: ResultReq): Unit = rreq.provideOne {
    if (literal.is[ScInterpolatedStringLiteral]) {
      buildUnknownCall(literal, 0, ResultReq.Required)
    } else {
      push(literalToDfType(literal), ScalaStatementAnchor(literal))
    }
  }

  private def transformIfExpression(ifExpression: ScIf, rreq: ResultReq): Unit = {
    val returnType = ifExpression.`type`().getOrAny
    val elseLabel = newDeferredLabel()
    val endLabel = newDeferredLabel()

    transformExpression(ifExpression.condition, ResultReq.Required)
    addInstruction(new ConditionalGotoInstruction(elseLabel, DfTypes.FALSE, ifExpression.condition.orNull))

    addInstruction(new FinishElementInstruction(null))
    transformExpression(ifExpression.thenExpression, rreq)
    buildImplicitConversion(ifExpression.thenExpression, Some(returnType))
    goto(endLabel)
    anchorLabel(elseLabel)

    addInstruction(new FinishElementInstruction(null))
    transformExpression(ifExpression.elseExpression, rreq)
    ifExpression.elseExpression.foreach(expression => buildImplicitConversion(Some(expression), Some(returnType)))
    anchorLabel(endLabel)

    addInstruction(new FinishElementInstruction(ifExpression))
  }

  private def transformReference(expression: ScReferenceExpression, rreq: ResultReq): Unit = {
    if (isReferenceExpressionInvocation(expression)) {
      transformInvocation(expression, rreq)
    } else {
      expression.qualifier.foreach { qualifier =>
        transformExpression(qualifier, ResultReq.None)
      }

      val expectedType = resolveExpressionType(expression)
      ScalaDfaVariableDescriptor.fromReferenceExpression(expression) match {
        case Some(descriptor) =>
          rreq.provideOne {
            pushVariable(descriptor, expression)
            buildImplicitConversion(Some(expression), Some(expectedType))
          }
        case _ =>
          buildUnknownCall(expression, 0, rreq)
      }
    }
  }

  private def transformTypedExpression(typedExpression: ScTypedExpression, rreq: ResultReq): Unit = {
    transformExpression(typedExpression.expr, rreq)
  }

  private def transformNewTemplateDefinition(newTemplateDefinition: ScNewTemplateDefinition, rreq: ResultReq): Unit = {
    transformInvocation(newTemplateDefinition, rreq)
  }

  private def transformAssignment(assignment: ScAssignment, rreq: ResultReq): Unit = {
    assignment.leftExpression match {
      case reference: ScReferenceExpression =>
        ScalaDfaVariableDescriptor.fromReferenceExpression(reference) match {
          case Some(descriptor) => val definedType = resolveExpressionType(assignment.leftExpression)
            assignVariableValue(descriptor, assignment.rightExpression, definedType)
            pushUnknownValue(rreq)
          case _ =>
            buildUnknownCall(assignment, 0, rreq)
        }
      case _ =>
        buildUnknownCall(assignment, 0, rreq)
    }
  }

  private def transformDoWhileLoop(doWhileLoop: ScDo, rreq: ResultReq): Unit = {
    // TODO implement transformation
    unsupported(doWhileLoop) {
      buildUnknownCall(doWhileLoop, 0, rreq)
    }
  }

  private def transformWhileLoop(whileLoop: ScWhile, rreq: ResultReq): Unit = {
    // TODO implement transformation
    unsupported(whileLoop) {
      buildUnknownCall(whileLoop, 0, rreq)
    }
  }

  private def transformForExpression(forExpression: ScFor, rreq: ResultReq): Unit = {
    forExpression.desugared() match {
      case Some(desugared) =>
        transformExpression(desugared, rreq)
      case _ =>
        buildUnknownCall(forExpression, 0, rreq)
    }
  }

  private def transformMatchExpression(matchExpression: ScMatch, rreq: ResultReq): Unit = {
    // TODO implement transformation
    unsupported(matchExpression) {
      buildUnknownCall(matchExpression, 0, rreq)
    }
  }

  //noinspection UnstableApiUsage
  private def transformThrowStatement(throwStatement: ScThrow, rreq: ResultReq): Unit = {
    val exceptionExpression = throwStatement.expression
    exceptionExpression match {
      case Some(exception) =>
        transformExpression(exception, ResultReq.None)
        val psiType = exception.`type`().getOrAny.toPsiType
        val transfer = new ExceptionTransfer(TypeConstraints.instanceOf(psiType))
        addInstruction(new ThrowInstruction(transferValue(transfer), exception))
        pushUnknownValue(rreq)
      case _ =>
        buildUnknownCall(throwStatement, 0, rreq)
    }
  }

  private def transformReturnStatement(returnStatement: ScReturn): Unit = {
    returnStatement.expr match {
      case Some(expression) =>
        transformExpression(expression, ResultReq.Required)
      case _ =>
        pushUnknownValue()
    }

    ret(returnStatement.expr)
  }
}
