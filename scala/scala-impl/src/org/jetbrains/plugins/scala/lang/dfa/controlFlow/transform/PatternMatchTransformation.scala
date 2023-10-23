package org.jetbrains.plugins.scala.lang.dfa.controlFlow.transform

import com.intellij.codeInspection.dataFlow.java.inst.BooleanBinaryInstruction
import com.intellij.codeInspection.dataFlow.lang.ir.ControlFlow.DeferredOffset
import com.intellij.codeInspection.dataFlow.types.DfTypes
import com.intellij.codeInspection.dataFlow.value.RelationType
import org.jetbrains.plugins.scala.lang.dfa.analysis.framework.ScalaPsiElementDfaAnchor
import org.jetbrains.plugins.scala.lang.dfa.controlFlow.ScalaDfaControlFlowBuilder
import org.jetbrains.plugins.scala.lang.dfa.controlFlow.transform.InstructionBuilder.{DeferredLabel, StackValue}
import org.jetbrains.plugins.scala.lang.dfa.utils.ScalaDfaConstants
import org.jetbrains.plugins.scala.lang.psi.api.base.patterns.{ScCaseClause, ScCaseClauses, ScLiteralPattern, ScPattern, ScWildcardPattern}

import scala.annotation.tailrec

trait PatternMatchTransformation { this: ScalaDfaControlFlowBuilder =>
  def transformCaseClauses(testValue: StackValue, caseClauses: ScCaseClauses, rreq: ResultReq): rreq.Result =
    transformCaseClauses(testValue, caseClauses.caseClauses, rreq, Some(caseClauses))

  def transformCaseClauses(testValue: StackValue, caseClauses: Option[ScCaseClauses], rreq: ResultReq): rreq.Result =
    caseClauses match {
      case Some(caseClauses) => transformCaseClauses(testValue, caseClauses, rreq)
      case None => transformCaseClauses(testValue, Seq.empty, rreq, None)
    }

  private def transformCaseClauses(testValue: StackValue,
                                   clauses: Seq[ScCaseClause],
                                   rreq: ResultReq,
                                   anchor: Option[ScCaseClauses]): rreq.Result = {
    val stack = stackSnapshot
    val endLabel = newDeferredLabel()
    val results = Seq.newBuilder[rreq.Result]

    @tailrec
    def transformClauses(clauses: Seq[ScCaseClause]): Unit =
      clauses match {
        case Seq() =>
          pop(testValue)
          throws(ScalaDfaConstants.Exceptions.ScalaMatchError, anchor.orNull)
          results += pushUnknownValue(rreq)
        case cc +: rest =>
          val (result, failLabel) = transformCaseClause(testValue, cc, rreq)
          results += result
          failLabel match {
            case Some(failLabel) =>
              goto(endLabel)
              restore(stack)
              anchorLabel(failLabel)
              transformClauses(rest)
            case None =>
              // catchall was used, so we don't need to generate match error
          }
      }

    if (clauses.isEmpty) {
      pop(testValue)
      pushUnknownValue(rreq)
    } else {
      transformClauses(clauses)
      val result = rreq.mapM(results.result()) { results =>
        joinHere(results)
      }
      anchorLabel(endLabel)
      result
    }
  }

  private def transformCaseClause(testValue: StackValue, caseClause: ScCaseClause, rreq: ResultReq): (rreq.Result, Option[DeferredLabel]) = {
    val failLabel =
      caseClause.pattern match {
        case Some(pattern) =>
          transformPattern(testValue, pattern)
        case None =>
          val unknown = buildUnknownCall(ResultReq.Required)
          val failLabel = newDeferredLabel()
          gotoIf(unknown, DfTypes.FALSE, failLabel)
          Some(failLabel)
      }

    val failLabelAfterGuard =
      caseClause.guard match {
        case Some(guard) =>
          val guardFailLabel = failLabel.getOrElse(newDeferredLabel())
          val guardResult = transformExpression(guard.expr, ResultReq.Required)
          gotoIf(guardResult, DfTypes.FALSE, guardFailLabel)
          Some(guardFailLabel)
        case None =>
          failLabel
      }

    pop(testValue)
    val result = transformExpression(caseClause.expr, rreq)

    (result, failLabelAfterGuard)
  }

  def transformPattern(testValue: StackValue, pattern: ScPattern): Option[DeferredLabel] = {
    pattern match {
      case lit: ScLiteralPattern => transformLiteralPattern(testValue, lit)
      case _: ScWildcardPattern => None
      case _ =>
        unsupported(pattern) {
          val result = buildUnknownCall(ResultReq.Required)
          val failLabel = newDeferredLabel()
          gotoIf(result, DfTypes.FALSE, failLabel)
          Some(failLabel)
        }
    }
  }

  private def transformLiteralPattern(testValue: StackValue,
                                      pattern: ScLiteralPattern): Some[DeferredLabel] = {
    val value = dup(testValue)
    val failLabel = newDeferredLabel()
    val lit = transformLiteral(pattern.getLiteral, ResultReq.Required)
    val result = binaryBoolOp(value, lit, RelationType.EQ, forceEqualityByContent = true, ScalaPsiElementDfaAnchor(pattern))
    gotoIf(result, DfTypes.FALSE, failLabel, anchor = pattern)
    Some(failLabel)
  }
}
