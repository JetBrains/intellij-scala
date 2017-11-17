package org.jetbrains.plugins.scala.codeInspection.booleans

import com.intellij.codeInspection.{ProblemHighlightType, ProblemsHolder}
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import org.jetbrains.plugins.scala.codeInspection.booleans.SimplifyBooleanUtil.isOfBooleanType
import org.jetbrains.plugins.scala.codeInspection.{AbstractFixOnPsiElement, AbstractInspection}
import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.psi.api.base.ScBooleanLiteral
import org.jetbrains.plugins.scala.lang.psi.api.base.patterns._
import org.jetbrains.plugins.scala.lang.psi.api.expr._
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementFactory.createExpressionFromText

import scala.language.implicitConversions

class SimplifyBooleanMatchInspection extends AbstractInspection("SimplifyBooleanMatch", "Trivial match can be simplified") {

  override protected def actionFor(implicit holder: ProblemsHolder): PartialFunction[PsiElement, Any] = {
    case stmt: ScMatchStmt if stmt.isValid && SimpleBooleanMatchUtil.isSimpleBooleanMatchStmt(stmt) =>
      val toHighlight = Option(stmt.findFirstChildByType(ScalaTokenTypes.kMATCH)).getOrElse(stmt)
      holder.registerProblem(toHighlight, getDisplayName, ProblemHighlightType.GENERIC_ERROR_OR_WARNING, new SimplifyBooleanMatchToIfStmtQuickFix(stmt))
    case _ =>
  }
}

class SimplifyBooleanMatchToIfStmtQuickFix(stmt: ScMatchStmt) extends AbstractFixOnPsiElement("Simplify match to if statement", stmt) {

  override protected def doApplyFix(scStmt: ScMatchStmt)
                                   (implicit project: Project): Unit = {
    if (SimpleBooleanMatchUtil.isSimpleBooleanMatchStmt(scStmt)) {
      scStmt.replaceExpression(SimpleBooleanMatchUtil.simplifyMatchStmt(scStmt), removeParenthesis = false)
    }
  }
}

object SimpleBooleanMatchUtil {

  def isSimpleBooleanMatchStmt(stmt: ScMatchStmt): Boolean = {
    if (stmt.expr.isEmpty || !isOfBooleanType(stmt.expr.get)) return false

    if (!stmt.caseClauses.forall(_.expr.isDefined)) return false
    stmt.caseClauses.size match {
      case 1 => getFirstBooleanClauseAndValue(stmt).isDefined
      case 2 => isValidClauses(stmt)
      case _ => false
    }
  }

  def simplifyMatchStmt(stmt: ScMatchStmt): ScExpression = {
    if (!isSimpleBooleanMatchStmt(stmt) || stmt.expr.isEmpty) return stmt
    stmt.caseClauses.size match {
      case 1 => simplifySingleBranchedStmt(stmt)
      case 2 => simplifyDualBranchedStmt(stmt)
      case _ => stmt
    }
  }

  private def simplifySingleBranchedStmt(stmt: ScMatchStmt): ScExpression = {
    getFirstBooleanClauseAndValue(stmt) match {
      case None => stmt
      case Some((clause, value)) =>
        val exprText = if (value) stmt.expr.get.getText else "!" + getParenthesisedText(stmt.expr.get)
        createExpressionFromText(s"if ($exprText){ ${getTextWithoutBraces(clause)} }")(stmt.projectContext)
    }
  }

  def simplifyDualBranchedStmt(stmt: ScMatchStmt): ScExpression = {
    getPartitionedClauses(stmt) match {
      case Some((trueClause, falseClause)) if trueClause.expr.nonEmpty && falseClause.expr.nonEmpty =>
        val exprText = stmt.expr.get.getText
        createExpressionFromText(
          s"""
             |if ($exprText) {
             |${getTextWithoutBraces(trueClause)}
             |} else {
             |${getTextWithoutBraces(falseClause)}
             |}
           """.stripMargin)(stmt.projectContext)
      case _ => stmt
    }
  }

  private def getPartitionedClauses(stmt: ScMatchStmt): Option[(ScCaseClause, ScCaseClause)] = {
    if (isSimpleClauses(stmt)) {
      val parts = stmt.caseClauses.partition {
        case ScCaseClause((Some(p: ScPattern), _, _)) => booleanConst(p).get
      }
      parts match {
        case (Seq(trueClause), Seq(falseClause)) => Some(trueClause, falseClause)
        case _ => None
      }
    } else {
      (getFirstBooleanClauseAndValue(stmt), getFirstWildcardClause(stmt)) match {
        case (Some((booleanClause, value)), Some(wildcardClause)) =>
          if (value) Some((booleanClause, wildcardClause))
          else Some((wildcardClause, booleanClause))
        case _ => None
      }
    }
  }

  private def getFirstBooleanClauseAndValue(stmt: ScMatchStmt): Option[(ScCaseClause, Boolean)] = stmt.caseClauses.collectFirst {
    case clause@ScCaseClause(Some(p: ScPattern), _, _) if booleanConst(p).isDefined => (clause, booleanConst(p).get)
  }

  private def getFirstWildcardClause(stmt: ScMatchStmt): Option[ScCaseClause] = stmt.caseClauses.collectFirst {
    case clause@ScCaseClause(Some(_: ScWildcardPattern), _, _) => clause
  }

  private def isSimpleClauses(stmt: ScMatchStmt): Boolean = stmt.caseClauses.forall {
    case ScCaseClause(Some(p: ScPattern), None, _) => booleanConst(p).isDefined
    case _ => false
  }

  private val BracedBlockRegex = """(?ms)\{(.+)\}""".r

  private def getTextWithoutBraces(clause: ScCaseClause): String = clause.expr match {
    case Some(block: ScBlock) =>
      block.getText match {
        case BracedBlockRegex(code) => code.trim
        case _ => block.getText
      }
    case Some(t) => t.getText
    case _ => clause.getText
  }

  private def getParenthesisedText(expr: ScExpression): String = {
    expr match {
      case e: ScInfixExpr => e match {
        case ScParenthesisedExpr(expr: ScExpression) => expr.getText
        case _ => s"(${e.getText})"
      }
      case _ => expr.getText
    }
  }

  private def isValidClauses(stmt: ScMatchStmt): Boolean = getPartitionedClauses(stmt).nonEmpty

  private def booleanConst(expr: ScPattern): Option[Boolean] = expr match {
    case pattern: ScLiteralPattern => pattern.getLiteral match {
      case ScBooleanLiteral(value) => Some(value)
      case _ => None
    }
    case _ => None
  }
}
