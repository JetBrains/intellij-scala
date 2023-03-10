package org.jetbrains.plugins.scala.codeInspection.booleans

import com.intellij.codeInspection.{LocalInspectionTool, ProblemHighlightType, ProblemsHolder}
import com.intellij.openapi.project.Project
import org.jetbrains.plugins.scala.codeInspection.booleans.SimplifyBooleanUtil.isOfBooleanType
import org.jetbrains.plugins.scala.codeInspection.{AbstractFixOnPsiElement, PsiElementVisitorSimple, ScalaInspectionBundle}
import org.jetbrains.plugins.scala.extensions.StringExt
import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiUtil
import org.jetbrains.plugins.scala.lang.psi.api.base.literals.ScBooleanLiteral
import org.jetbrains.plugins.scala.lang.psi.api.base.patterns._
import org.jetbrains.plugins.scala.lang.psi.api.expr._
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementFactory.createElementFromText
import org.jetbrains.plugins.scala.project.ScalaFeatures

import scala.language.implicitConversions

class SimplifyBooleanMatchInspection extends LocalInspectionTool {

  override def buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitorSimple = {
    case stmt: ScMatch if stmt.isValid && SimpleBooleanMatchUtil.isSimpleBooleanMatchStmt(stmt) =>
      val toHighlight = stmt.findFirstChildByType(ScalaTokenTypes.kMATCH).getOrElse(stmt)
      holder.registerProblem(toHighlight, getDisplayName, ProblemHighlightType.GENERIC_ERROR_OR_WARNING, new SimplifyBooleanMatchToIfStmtQuickFix(stmt))
    case _ =>
  }
}

class SimplifyBooleanMatchToIfStmtQuickFix(stmt: ScMatch) extends AbstractFixOnPsiElement(ScalaInspectionBundle.message("simplify.match.to.if.statement"), stmt) {

  override protected def doApplyFix(scStmt: ScMatch)
                                   (implicit project: Project): Unit = {
    if (SimpleBooleanMatchUtil.isSimpleBooleanMatchStmt(scStmt)) {
      val newExpr = SimpleBooleanMatchUtil.simplifyMatchStmt(scStmt)(project, scStmt)
      scStmt.replaceExpression(newExpr, removeParenthesis = false)
    }
  }
}

object SimpleBooleanMatchUtil {

  def isSimpleBooleanMatchStmt(stmt: ScMatch): Boolean = {
    if (stmt.expression.isEmpty || !isOfBooleanType(stmt.expression.get)) return false

    if (!stmt.clauses.forall(_.expr.isDefined)) return false
    stmt.clauses.size match {
      case 1 => getFirstBooleanClauseAndValue(stmt).isDefined
      case 2 => isValidClauses(stmt)
      case _ => false
    }
  }

  def simplifyMatchStmt(stmt: ScMatch)(implicit project: Project, features: ScalaFeatures): ScExpression = {
    if (!isSimpleBooleanMatchStmt(stmt) || stmt.expression.isEmpty) return stmt
    stmt.clauses.size match {
      case 1 => simplifySingleBranchedStmt(stmt)
      case 2 => simplifyDualBranchedStmt(stmt)
      case _ => stmt
    }
  }

  private def simplifySingleBranchedStmt(stmt: ScMatch)(implicit project: Project, features: ScalaFeatures): ScExpression = {
    getFirstBooleanClauseAndValue(stmt) match {
      case None => stmt
      case Some((clause, value)) =>
        val exprText = if (value) stmt.expression.get.getText else "!" + getParenthesisedText(stmt.expression.get)
        val ifStmt = createElementFromText[ScIf](s"if ($exprText){ ${getTextWithoutBraces(clause)} }", stmt)
        ScalaPsiUtil.convertIfToBracelessIfNeeded(ifStmt, recursive = true)
    }
  }

  def simplifyDualBranchedStmt(stmt: ScMatch)(implicit project: Project, features: ScalaFeatures): ScExpression = {
    getPartitionedClauses(stmt) match {
      case Some((trueClause, falseClause)) if trueClause.expr.nonEmpty && falseClause.expr.nonEmpty =>
        val exprText = stmt.expression.get.getText
        val ifStmt = createElementFromText[ScIf](
          s"""
             |if ($exprText) {
             |${getTextWithoutBraces(trueClause)}
             |} else {
             |${getTextWithoutBraces(falseClause)}
             |}
           """.stripMargin, stmt)
        ScalaPsiUtil.convertIfToBracelessIfNeeded(ifStmt, recursive = true)
      case _ => stmt
    }
  }

  private def getPartitionedClauses(stmt: ScMatch): Option[(ScCaseClause, ScCaseClause)] = {
    if (isSimpleClauses(stmt)) {
      val parts = stmt.clauses.partition {
        case BooleanClause(value) => value
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

  private def getFirstBooleanClauseAndValue(stmt: ScMatch): Option[(ScCaseClause, Boolean)] = stmt.clauses.collectFirst {
    case clause@BooleanClause(value) => (clause, value)
  }

  private def getFirstWildcardClause(stmt: ScMatch): Option[ScCaseClause] = stmt.clauses.collectFirst {
    case clause@ScCaseClause(Some(_: ScWildcardPattern), _, _) => clause
  }

  private def isSimpleClauses(stmt: ScMatch): Boolean = stmt.clauses.forall {
    case clause@BooleanClause(_) => clause.guard.isEmpty
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
        case _ => e.getText.parenthesize()
      }
      case _ => expr.getText
    }
  }

  private def isValidClauses(stmt: ScMatch): Boolean = getPartitionedClauses(stmt).nonEmpty

  private object BooleanClause {

    def unapply(clause: ScCaseClause): Option[Boolean] = clause.pattern match {
      case Some(ScLiteralPattern(ScBooleanLiteral(value))) => Some(value)
      case _ => None
    }
  }
}
