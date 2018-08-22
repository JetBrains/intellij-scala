package org.jetbrains.plugins.scala
package codeInspection.controlFlow

import com.intellij.codeInspection.ProblemsHolder
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.util.PsiTreeUtil
import org.jetbrains.plugins.scala.codeInspection.{AbstractFixOnPsiElement, AbstractInspection, InspectionBundle}
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.psi.api.base.patterns.{ScCaseClause, ScCaseClauses}
import org.jetbrains.plugins.scala.lang.psi.api.expr._
import org.jetbrains.plugins.scala.lang.psi.api.statements.{ScFunctionDefinition, ScPatternDefinition, ScVariableDefinition}
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementFactory
import org.jetbrains.plugins.scala.lang.psi.types.result._
import org.jetbrains.plugins.scala.util.IntentionAvailabilityChecker
import org.jetbrains.plugins.scala.util.SideEffectsUtil._

/**
  * Nikolay.Tropin
  * 2014-09-22
  */
class ScalaUnusedExpressionInspection extends AbstractInspection("Unused expression") {

  import ScalaUnusedExpressionInspection._

  override protected def actionFor(implicit holder: ProblemsHolder): PartialFunction[PsiElement, Any] = {
    case expr: ScExpression if IntentionAvailabilityChecker.checkInspection(this, expr.getParent) =>

      val valueIsNotUsed = canResultInSideEffectsOnly(expr)

      if (valueIsNotUsed) {

        val message =
          if (hasNoSideEffects(expr)) Some(InspectionBundle.message("unused.expression.no.side.effects"))
          else if (mayOnlyThrow(expr)) Some(InspectionBundle.message("unused.expression.throws"))
          else None

        message.foreach { m =>
          val removeElemFix = new RemoveElementQuickFix(expr)
          val addReturnKeywordFix = PsiTreeUtil.getParentOfType(expr, classOf[ScFunctionDefinition]) match {
            case null => Seq.empty
            case fun if !fun.returnType.getOrAny.isUnit => Seq(new AddReturnQuickFix(expr))
            case _ => Seq.empty
          }

          holder.registerProblem(expr, m, removeElemFix +: addReturnKeywordFix: _*)
        }
      }
  }

}

object ScalaUnusedExpressionInspection {

  private def isLastInBlock(expr: ScExpression): Boolean = expr match {
    case ChildOf(bl: ScBlock) => bl.lastExpr.contains(expr)
    case ChildOf(_: ScPatternDefinition | _: ScFunctionDefinition | _: ScVariableDefinition) =>
      !expr.isInstanceOf[ScBlock]
    case _ => false
  }

  private def isInBlock(expr: ScExpression): Boolean = expr match {
    case ChildOf(_: ScBlock) => true
    case _ => false
  }

  private def canResultInSideEffectsOnly(expr: ScExpression): Boolean = {
    def isNotLastInBlock: Boolean = {
      val parents = expr.parentsInFile.takeWhile {
        case ms: ScMatchStmt if ms.expr.exists(PsiTreeUtil.isAncestor(_, expr, false)) => false
        case ifStmt: ScIfStmt if ifStmt.condition.exists(PsiTreeUtil.isAncestor(_, expr, false)) => false
        case _: ScBlock | _: ScParenthesisedExpr | _: ScIfStmt |
             _: ScCaseClause | _: ScCaseClauses | _: ScMatchStmt |
             _: ScTryStmt | _: ScCatchBlock => true
        case _ => false
      }
      (expr +: parents.toSeq).exists {
        case e: ScExpression => isInBlock(e) && !isLastInBlock(e)
        case _ => false
      }
    }

    def isInReturnPositionForUnitFunction: Boolean = {
      Option(PsiTreeUtil.getParentOfType(expr, classOf[ScFunctionDefinition])) match {
        case Some(fun) if fun.returnType.exists(_.isUnit) => fun.returnUsages(expr)
        case _ => false
      }
    }

    isNotLastInBlock || isInReturnPositionForUnitFunction
  }

  class AddReturnQuickFix(expression: ScExpression) extends AbstractFixOnPsiElement(
    "Add return keyword",
    expression
  ) {

    override protected def doApplyFix(expression: ScExpression)
                                     (implicit project: Project): Unit = {
      val retStmt = ScalaPsiElementFactory.createExpressionWithContextFromText(s"return ${expression.getText}", expression.getContext, expression)
      expression.replaceExpression(retStmt, removeParenthesis = true)
    }
  }

  class RemoveElementQuickFix(expression: ScExpression) extends AbstractFixOnPsiElement(
    "Remove expression",
    expression
  ) {

    override protected def doApplyFix(expression: ScExpression)
                                     (implicit project: Project): Unit = {
      expression.delete()
    }
  }

}