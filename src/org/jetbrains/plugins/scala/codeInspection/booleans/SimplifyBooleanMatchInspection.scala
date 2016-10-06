package org.jetbrains.plugins.scala.codeInspection.booleans

import com.intellij.codeInspection.{ProblemHighlightType, ProblemsHolder}
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import org.jetbrains.plugins.scala.codeInspection.{AbstractFixOnPsiElement, AbstractInspection}
import org.jetbrains.plugins.scala.lang.psi.api.base.ScLiteral
import org.jetbrains.plugins.scala.lang.psi.api.base.patterns._
import org.jetbrains.plugins.scala.lang.psi.api.expr._
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementFactory.createExpressionFromText
import org.jetbrains.plugins.scala.lang.psi.types.api.TypeSystem
import org.jetbrains.plugins.scala.lang.psi.types.result.TypingContext
import org.jetbrains.plugins.scala.lang.psi.types.{ScTypeExt, api}

class SimplifyBooleanMatchInspection extends AbstractInspection("SimplifyBooleanMatch", "Trivial match can be simplified") {
  def actionFor(holder: ProblemsHolder): PartialFunction[PsiElement, Any] = {
    case stmt: ScMatchStmt if stmt.isValid && SimpleBooleanMatchUtil.isSimpleBooleanMatchStmt(stmt) =>
      holder.registerProblem(stmt, getDisplayName, ProblemHighlightType.GENERIC_ERROR_OR_WARNING, new SimplifyBooleanMatchToIfStmtQuickFix(stmt))
    case _ =>
  }
}

class SimplifyBooleanMatchToIfStmtQuickFix(stmt: ScMatchStmt) extends AbstractFixOnPsiElement("Simplify match to if statement", stmt) {
  override def doApplyFix(project: Project) {
    val scStmt = getElement
    if (scStmt.isValid && SimpleBooleanMatchUtil.isSimpleBooleanMatchStmt(scStmt)) {
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
    val clause = stmt.caseClauses.head

    val booleanCauseAndValue = getFirstBooleanClauseAndValue(stmt)
    if (booleanCauseAndValue.isEmpty) return stmt

    val exprText = if (booleanCauseAndValue.get._2) stmt.expr.get.text else "!" + getParenthesisedText(stmt.expr.get)
    createExpressionFromText(s"if ($exprText){ ${getTextWithoutBraces(clause)} }")(stmt.manager)
  }

  def simplifyDualBranchedStmt(stmt: ScMatchStmt): ScExpression = {
    val clauses = getPartitionedClauses(stmt)
    if (clauses.isEmpty) return stmt

    if (clauses.get._1.expr.isEmpty || clauses.get._2.expr.isEmpty) return stmt
    val exprText = stmt.expr.get.text
    createExpressionFromText(
      s"""
         |if ($exprText) {
         |${getTextWithoutBraces(clauses.get._1)}
         |} else {
         |${getTextWithoutBraces(clauses.get._2)}
         |}
           """.stripMargin)(stmt.manager)
  }

  private def getPartitionedClauses(stmt: ScMatchStmt): Option[(ScCaseClause, ScCaseClause)] = {
    if (isSimpleClauses(stmt)) {
      val parts = stmt.caseClauses.partition({ case clause@ScCaseClause((Some(p: ScPattern), _, _)) => booleanConst(p).get })
      if (parts._1.isEmpty || parts._2.isEmpty) return None

      Some(parts._1.head, parts._2.head)
    } else {
      val wildcardCause = getFirstWildcardClause(stmt)
      val booleanCause = getFirstBooleanClauseAndValue(stmt)
      if (wildcardCause.isEmpty || booleanCause.isEmpty) return None

      if (booleanCause.get._2) {
        Some(booleanCause.get._1, wildcardCause.get)
      } else {
        Some(wildcardCause.get, booleanCause.get._1)
      }
    }
  }

  private def getFirstBooleanClauseAndValue(stmt: ScMatchStmt): Option[(ScCaseClause, Boolean)] = stmt.caseClauses.collectFirst {
    case clause@ScCaseClause(Some(p: ScPattern), _, _) if booleanConst(p).isDefined => (clause, booleanConst(p).get)
  }

  private def getFirstWildcardClause(stmt: ScMatchStmt): Option[ScCaseClause] = stmt.caseClauses.collectFirst {
    case clause@ScCaseClause(Some(p: ScWildcardPattern), _, _) => clause
  }

  private def isSimpleClauses(stmt: ScMatchStmt): Boolean = stmt.caseClauses
    .forall {
      case clause@ScCaseClause(Some(p: ScPattern), None, _) => booleanConst(p).isDefined
      case _ => false
    }

  val BracedBlockRegex = """(?ms)\{(.+)\}""".r

  private def getTextWithoutBraces(clause: ScCaseClause): String = clause.expr match {
    case Some(block: ScBlock) =>
      block.text match {
        case BracedBlockRegex(code) => code.trim
        case _ => block.text
      }
    case Some(t) => t.text
    case _ => clause.text
  }

  private def getParenthesisedText(expr: ScExpression): String = {
    expr match {
      case e: ScInfixExpr => e match {
        case ScParenthesisedExpr(expr: ScExpression) => expr.text
        case _ => s"(${e.text})"
      }
      case _ => expr.text
    }
  }

  private def isOfBooleanType(expr: ScExpression)
                             (implicit typeSystem: TypeSystem = expr.typeSystem): Boolean = {
    expr.getType(TypingContext.empty).getOrAny.weakConforms(api.Boolean)
  }


  private def isValidClauses(stmt: ScMatchStmt): Boolean = {
    if (isSimpleClauses(stmt)) return true
    getFirstWildcardClause(stmt).isDefined && getFirstBooleanClauseAndValue(stmt).isDefined
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
