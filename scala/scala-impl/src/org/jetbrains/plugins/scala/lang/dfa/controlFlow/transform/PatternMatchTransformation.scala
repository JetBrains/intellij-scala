package org.jetbrains.plugins.scala.lang.dfa.controlFlow.transform

import com.intellij.codeInspection.dataFlow.java.inst.BooleanBinaryInstruction
import com.intellij.codeInspection.dataFlow.lang.ir.ControlFlow.DeferredOffset
import com.intellij.codeInspection.dataFlow.types.DfTypes
import com.intellij.codeInspection.dataFlow.value.RelationType
import org.jetbrains.plugins.scala.lang.dfa.analysis.framework.ScalaPsiElementDfaAnchor
import org.jetbrains.plugins.scala.lang.dfa.controlFlow.ScalaDfaControlFlowBuilder
import org.jetbrains.plugins.scala.lang.dfa.utils.ScalaDfaConstants
import org.jetbrains.plugins.scala.lang.psi.api.base.patterns.{ScCaseClause, ScCaseClauses, ScLiteralPattern, ScPattern, ScWildcardPattern}

import scala.annotation.tailrec

trait PatternMatchTransformation { this: ScalaDfaControlFlowBuilder =>
  def transformCaseClauses(caseClauses: ScCaseClauses, rreq: ResultReq): Unit =
    transformCaseClauses(caseClauses.caseClauses, rreq, Some(caseClauses))

  def transformCaseClauses(caseClauses: Option[ScCaseClauses], rreq: ResultReq): Unit =
    caseClauses match {
      case Some(caseClauses) => transformCaseClauses(caseClauses, rreq)
      case None => transformCaseClauses(Seq.empty, rreq, None)
    }

  private def transformCaseClauses(clauses: Seq[ScCaseClause], rreq: ResultReq, anchor: Option[ScCaseClauses]): Unit = {
    val endLabel = newDeferredLabel()

    @tailrec
    def transformClauses(clauses: Seq[ScCaseClause]): Unit =
      clauses match {
        case Seq() =>
          throws(ScalaDfaConstants.Exceptions.ScalaMatchError, anchor.orNull)
        case cc +: rest =>
          transformCaseClause(cc, matcheeNeededOnFail = rest.nonEmpty, rreq) match {
            case Some(failLabel) =>
              goto(endLabel)
              anchorLabel(failLabel)
              transformClauses(rest)
            case None =>
              // catchall was used, so we don't need to generate match error
          }
      }

    if (clauses.isEmpty) {
      pop()
    }

    transformClauses(clauses)
    anchorLabel(endLabel)
  }

  private def transformCaseClause(caseClause: ScCaseClause, matcheeNeededOnFail: Boolean, rreq: ResultReq): Option[DeferredOffset] = {
    val failLabel =
      caseClause.pattern match {
        case Some(pattern) =>
          transformPattern(pattern, matcheeNeededOnFail)
        case None =>
          buildUnknownCall(0, ResultReq.Required)
          val failLabel = newDeferredLabel()
          gotoIfTosEquals(DfTypes.FALSE, failLabel)
          Some(failLabel)
      }

    val failLabelAfterGuard =
      caseClause.guard match {
        case Some(guard) =>
          val guardFailLabel = failLabel.getOrElse(newDeferredLabel())
          transformExpression(guard.expr, ResultReq.Required)
          gotoIfTosEquals(DfTypes.FALSE, guardFailLabel)
          Some(guardFailLabel)
        case None =>
          failLabel
      }

    transformExpression(caseClause.expr, rreq)

    failLabelAfterGuard
  }

  def transformPattern(pattern: ScPattern, matcheeNeededOnFail: Boolean): Option[DeferredOffset] = {
    pattern match {
      case lit: ScLiteralPattern => transformLiteralPattern(lit, matcheeNeededOnFail)
      case _: ScWildcardPattern =>
        pop()
        None
      case _ =>
        unsupported(pattern) {
          buildUnknownCall(0, ResultReq.Required)
          val failLabel = newDeferredLabel()
          gotoIfTosEquals(DfTypes.FALSE, failLabel)
          Some(failLabel)
        }
    }
  }

  private def transformLiteralPattern(pattern: ScLiteralPattern, matcheeNeededOnFail: Boolean): Some[DeferredOffset] = {
    if (matcheeNeededOnFail) {
      dup()
    }
    val failLabel = newDeferredLabel()
    transformLiteral(pattern.getLiteral, ResultReq.Required)
    addInstruction(new BooleanBinaryInstruction(RelationType.EQ, true, ScalaPsiElementDfaAnchor(pattern)))
    gotoIfTosEquals(DfTypes.FALSE, failLabel, pattern)
    Some(failLabel)
  }
}
