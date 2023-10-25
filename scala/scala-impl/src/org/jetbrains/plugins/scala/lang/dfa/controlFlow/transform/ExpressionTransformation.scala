package org.jetbrains.plugins.scala.lang.dfa.controlFlow.transform

import com.intellij.codeInspection.dataFlow.types.DfTypes
import com.intellij.psi.{CommonClassNames, PsiMethod}
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.dfa.analysis.framework.ScalaStatementAnchor
import org.jetbrains.plugins.scala.lang.dfa.controlFlow.{ScalaDfaControlFlowBuilder, ScalaDfaVariableDescriptor, TransformationFailedException}
import org.jetbrains.plugins.scala.lang.dfa.utils.ScalaDfaTypeUtils.{literalToDfType, resolveExpressionType}
import org.jetbrains.plugins.scala.lang.psi.api.base.{ScInterpolatedStringLiteral, ScLiteral}
import org.jetbrains.plugins.scala.lang.psi.api.expr._
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScTemplateDefinition
import org.jetbrains.plugins.scala.util.SAMUtil.isFunctionalExpression

trait ExpressionTransformation { this: ScalaDfaControlFlowBuilder =>
  def transformExpression(expr: ScExpression, rreq: ResultReq): rreq.Result = transformImplicitConversion(expr, rreq) {
    expr match {
      case someExpression if isUnsupportedPureExpressionType(someExpression) => pushUnknownValue(rreq)
      case someExpression if isUnsupportedImpureExpressionType(someExpression) => buildUnknownCall(rreq)
      case block: ScBlock => transformBlock(block, rreq)
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
      case returnStatement: ScReturn => transformReturnStatement(returnStatement, rreq)
      case invok: ScSelfInvocation => transformSelfInvocation(invok, rreq)
      case _: ScUnitExpr => rreq.result(pushUnit())
      case _: ScTemplateDefinition => pushUnknownValue(rreq)
      case _ => throw TransformationFailedException(expr, "Unsupported expression.")
    }
  }

  def transformExpression(element: Option[ScExpression], rreq: ResultReq): rreq.Result = element match {
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

  private def transformImplicitConversion(expr: ScExpression, rreq: ResultReq)(value: rreq.Result): rreq.Result =
    rreq.map(value) { value =>
      // todo: implicit conversions
      val from = expr.getNonValueType().toOption
      val to = expr.`type`().toOption

      convertPrimitiveIfNeeded(value, from, to)
    }

  private def transformBlock(block: ScBlock, rreq: ResultReq): rreq.Result = {
    val statements = block.statements
    if (statements.isEmpty) {
      pushUnit(rreq)
    } else {
      statements.init.foreach { statement =>
        transformStatement(statement, ResultReq.None)
      }

      val result = transformStatement(statements.last, rreq)
      finish(block)
      result
    }
  }

  private def transformParenthesisedExpression(parenthesised: ScParenthesisedExpr, rreq: ResultReq): rreq.Result =
    transformExpression(parenthesised.innerElement, rreq)

  def transformLiteral(literal: ScLiteral, rreq: ResultReq): rreq.Result = rreq.result {
    if (literal.is[ScInterpolatedStringLiteral]) {
      buildUnknownCall(ResultReq.Required)
    } else {
      push(literalToDfType(literal), ScalaStatementAnchor(literal))
    }
  }

  private def transformIfExpression(ifExpression: ScIf, rreq: ResultReq): rreq.Result = {
    //val returnType = ifExpression.`type`().getOrAny
    val beforeStack = stackSnapshot
    val elseLabel = newDeferredLabel()
    val endLabel = newDeferredLabel()

    val cond = transformExpression(ifExpression.condition, ResultReq.Required)
    gotoIf(cond, DfTypes.FALSE, elseLabel, anchor = ifExpression.condition.orNull)

    finish(null)
    val thenResultRaw = transformExpression(ifExpression.thenExpression, rreq)
    val thenResult = rreq.map(thenResultRaw) { result =>
      convertPrimitiveIfNeeded(
        result,
        ifExpression.thenExpression.flatMap(_.`type`().toOption),
        ifExpression.`type`().toOption,
      )
    }
    //buildImplicitConversion(ifExpression.thenExpression, Some(returnType))
    goto(endLabel)
    restore(beforeStack)
    anchorLabel(elseLabel)

    finish(null)
    val elseResult = ifExpression.elseExpression match {
      case Some(elseExpr) =>
        val elseResultRaw = transformExpression(elseExpr, rreq)
        rreq.map(elseResultRaw) { result =>
          convertPrimitiveIfNeeded(
            result,
            elseExpr.`type`().toOption,
            ifExpression.`type`().toOption,
          )
        }
      case None =>
        pushUnit(rreq)
    }
    //ifExpression.elseExpression.foreach(expression => buildImplicitConversion(Some(expression), Some(returnType)))

    val result = rreq.mapM(thenResult, elseResult)(joinHere)
    anchorLabel(endLabel)

    finish(ifExpression)
    result
  }

  private def transformReference(expression: ScReferenceExpression, rreq: ResultReq): rreq.Result = {
    if (isReferenceExpressionInvocation(expression)) {
      transformInvocation(expression, rreq)
    } else {
      expression.qualifier.foreach { qualifier =>
        transformExpression(qualifier, ResultReq.None)
      }

      //val expectedType = resolveExpressionType(expression)
      ScalaDfaVariableDescriptor.fromReferenceExpression(expression) match {
        case Some(descriptor) =>
          rreq.result {
            pushVariable(descriptor, expression)
            //buildImplicitConversion(Some(expression), Some(expectedType))
          }
        case _ =>
          buildUnknownCall(rreq)
      }
    }
  }

  private def transformTypedExpression(typedExpression: ScTypedExpression, rreq: ResultReq): rreq.Result = {
    transformExpression(typedExpression.expr, rreq)
  }

  private def transformNewTemplateDefinition(newTemplateDefinition: ScNewTemplateDefinition, rreq: ResultReq): rreq.Result = {
    transformInvocation(newTemplateDefinition, rreq)
  }

  private def transformAssignment(assignment: ScAssignment, rreq: ResultReq): rreq.Result = {
    assignment.leftExpression match {
      case reference: ScReferenceExpression =>
        ScalaDfaVariableDescriptor.fromReferenceExpression(reference) match {
          case Some(descriptor) => val definedType = resolveExpressionType(assignment.leftExpression)
            assignVariableValue(descriptor, assignment.rightExpression, definedType)
            pushUnknownValue(rreq)
          case _ =>
            buildUnknownCall(rreq)
        }
      case _ =>
        buildUnknownCall(rreq)
    }
  }

  private def transformDoWhileLoop(doWhileLoop: ScDo, rreq: ResultReq): rreq.Result = {
    val startLabel = newLabelHere()
    transformExpression(doWhileLoop.body, ResultReq.None)
    val condResult = transformExpression(doWhileLoop.condition, ResultReq.Required)
    gotoIf(condResult, DfTypes.TRUE, startLabel, anchor = doWhileLoop.condition.orNull)
    pushUnit(rreq)
  }

  private def transformWhileLoop(whileLoop: ScWhile, rreq: ResultReq): rreq.Result = {
    val stack = stackSnapshot
    val endLabel = newDeferredLabel()
    val beforeCondition = newLabelHere()
    val condValue = transformExpression(whileLoop.condition, ResultReq.Required)
    gotoIf(condValue, DfTypes.FALSE, endLabel, anchor = whileLoop.condition.orNull)
    transformExpression(whileLoop.expression, ResultReq.None)
    goto(beforeCondition)
    restore(stack)
    anchorLabel(endLabel)
    pushUnit(rreq)
  }

  private def transformForExpression(forExpression: ScFor, rreq: ResultReq): rreq.Result = {
    forExpression.desugared() match {
      case Some(desugared) =>
        transformExpression(desugared, rreq)
      case _ =>
        buildUnknownCall(rreq)
    }
  }

  private def transformMatchExpression(matchExpression: ScMatch, rreq: ResultReq): rreq.Result = {
    val testValue = transformExpression(matchExpression.expression, ResultReq.Required)
    transformCaseClauses(testValue, matchExpression.caseClauses, rreq)
  }

  //noinspection UnstableApiUsage
  private def transformThrowStatement(throwStatement: ScThrow, rreq: ResultReq): rreq.Result = {
    val exceptionExpression = throwStatement.expression
    exceptionExpression match {
      case Some(exception) =>
        transformExpression(exception, ResultReq.None)
        val psiType = exception.`type`().getOrAny.toPsiType
        throws(psiType.getCanonicalText, throwStatement)
        pushUnknownValue(rreq)
      case _ =>
        throws(CommonClassNames.JAVA_LANG_THROWABLE, throwStatement)
        pushUnknownValue(rreq)
    }
  }

  private def transformReturnStatement(returnStatement: ScReturn, rreq: ResultReq): rreq.Result = {
    transformExpression(returnStatement.expr, ResultReq.None)

    ret(returnStatement.expr)
    pushUnknownValue(rreq)
  }

  private def transformSelfInvocation(invocation: ScSelfInvocation, rreq: ResultReq): rreq.Result = {
    unsupported(invocation) {
      buildUnknownCall(rreq)
    }
  }
}
