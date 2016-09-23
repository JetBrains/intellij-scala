package org.jetbrains.plugins.scala.codeInspection.booleans

import com.intellij.codeInspection.{ProblemHighlightType, ProblemsHolder}
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import org.jetbrains.plugins.scala.codeInspection.booleans.SimpleBooleanMatchUtil.{isSimpleMatchStmt, simplifyMatchStmt}
import org.jetbrains.plugins.scala.codeInspection.{AbstractFixOnPsiElement, AbstractInspection}
import org.jetbrains.plugins.scala.lang.psi.api.base.ScLiteral
import org.jetbrains.plugins.scala.lang.psi.api.base.patterns.{ScCaseClause, ScLiteralPattern, ScPattern}
import org.jetbrains.plugins.scala.lang.psi.api.expr._
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementFactory.createExpressionFromText
import org.jetbrains.plugins.scala.lang.psi.types.api

class SimpleBooleanMatchInspection extends AbstractInspection("SimpleBooleanMatch", "Simplify trivial match to if") {
  def actionFor(holder: ProblemsHolder): PartialFunction[PsiElement, Any] = {
    case stmt: ScMatchStmt if isSimpleMatchStmt(stmt) =>
      holder.registerProblem(stmt, "Simplify trivial match to if", ProblemHighlightType.GENERIC_ERROR_OR_WARNING, new SimpleBooleanMatchQuickFix(stmt))
    case _ =>
  }
}

class SimpleBooleanMatchQuickFix(stmt: ScMatchStmt) extends AbstractFixOnPsiElement("Simplify trivial match to if", stmt) {
  override def doApplyFix(project: Project) {
    val scStmt = getElement
    if (!scStmt.isValid || !isSimpleMatchStmt(scStmt)) return
    scStmt.replaceExpression(simplifyMatchStmt(scStmt), removeParenthesis = false)
  }
}

object SimpleBooleanMatchUtil {

  def simplifyMatchStmt(stmt: ScMatchStmt): ScExpression = {
    if (!isSimpleMatchStmt(stmt) || stmt.expr.isEmpty) return stmt
    stmt.caseClauses.size match {
      case 1 => simplifySingleBranchedStmt(stmt)
      case 2 => simplifyDualBranchedStmt(stmt)
      case _ => stmt
    }
  }

  def simplifySingleBranchedStmt(stmt: ScMatchStmt): ScExpression = {
    if (stmt.expr.isEmpty) return stmt

    if (stmt.caseClauses.isEmpty) return stmt
    val clause = stmt.caseClauses.head

    clause.pattern match {
      case Some(pattern: ScLiteralPattern) =>
        val exprText = booleanConst(pattern) match {
          case Some(false) => "!" + getParenthesisedText(stmt.expr.get)
          case _ => stmt.expr.get.text
        }
        createExpressionFromText(s"if ($exprText){ ${getTextWithoutBraces(clause)} }")(stmt.manager)
      case _ => stmt
    }
  }

  def simplifyDualBranchedStmt(stmt: ScMatchStmt): ScExpression = {
    if (stmt.expr.isEmpty) return stmt

    val isSimpleClauses: Boolean = stmt.caseClauses
      .forall(c => c.pattern.isDefined && booleanConst(c.pattern.get).isDefined)
    if (!isSimpleClauses) return stmt

    val parts = stmt.caseClauses.partition(clause => booleanConst(clause.pattern.get).get)
    if (parts._1.isEmpty || parts._2.isEmpty) return stmt

    val clauses = (parts._1.head, parts._2.head)
    if (clauses._1.expr.isEmpty || clauses._2.expr.isEmpty) return stmt

    val exprText = stmt.expr.get.text
    createExpressionFromText(
      s"""
         |if ($exprText) {
         |${getTextWithoutBraces(clauses._1)}
         |} else {
         |${getTextWithoutBraces(clauses._2)}
         |}
           """.stripMargin)(stmt.manager)
  }

  val Regex = """(?ms)\{(.+)\}""".r

  def getTextWithoutBraces(clause: ScCaseClause): String = clause.expr match {
    case Some(block: ScBlock) =>
      block.text match {
        case Regex(code) => code.trim
        case _ => block.text
      }
    case Some(t) => t.text
    case _ => clause.text
  }

  def getParenthesisedText(expr: ScExpression): String = {
    expr match {
      case e: ScInfixExpr => e match {
        case _: ScParenthesisedExpr => expr.text
        case _ => s"(${e.text})"
      }
      case _ => expr.text
    }

  }

  def isSimpleMatchStmt(stmt: ScMatchStmt): Boolean = {
    if (stmt.expr.isEmpty) return false

    val exprType = stmt.expr.get.getType()
    if (exprType.isEmpty || exprType.get != api.Boolean) return false

    if (stmt.caseClauses.isEmpty || stmt.caseClauses.size > 2) return false
    if (!stmt.caseClauses.forall(_.expr.isDefined)) return false

    stmt.caseClauses.forall(p => {
      if (p.pattern.isEmpty) return false
      p.pattern.get match {
        case t: ScLiteralPattern => booleanConst(t).isDefined
        case _ => false
      }
    })
  }

  private def booleanConst(expr: ScPattern): Option[Boolean] = expr match {
    case pattern: ScLiteralPattern => pattern.getLiteral match {
      case literal: ScLiteral =>
        literal.getText match {
          case "true" => Some(true)
          case "false" => Some(false)
          case _ => None
        }
      case _ => None
    }
    case _ => None
  }
}
