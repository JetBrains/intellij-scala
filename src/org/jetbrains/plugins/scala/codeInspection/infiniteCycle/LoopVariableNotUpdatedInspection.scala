package org.jetbrains.plugins.scala
package codeInspection.infiniteCycle

import codeInspection.AbstractInspection
import lang.psi.api.statements.ScVariable
import com.intellij.codeInspection.{ProblemHighlightType, ProblemsHolder}
import com.intellij.psi.{PsiReference, PsiElement}
import extensions._
import lang.psi.ScalaPsiElement
import lang.psi.api.expr._

/**
 * Pavel Fatin
 */

class LoopVariableNotUpdatedInspection extends AbstractInspection("LoopVariableNotUpdatedInspection", "Loop variable not updated inside loop") {
  private val ComparisonOperators = Set("==", "!=", ">", "<", ">=", "<=")

  def actionFor(holder: ProblemsHolder) = {
    case ScWhileStmt(
      Some(ScInfixExpr((ref: ScReferenceExpression) && (Resolved(target@Parent(Parent(entity: ScVariable)), _)), ElementText(operator), _)),
      Some(body)) if !ref.isQualified && ComparisonOperators.contains(operator) && !isMutatedWithing(body, target) =>
        holder.registerProblem(ref.asInstanceOf[PsiReference],
          getDisplayName, ProblemHighlightType.GENERIC_ERROR_OR_WARNING)
  }

  private def isMutatedWithing(scope: ScalaPsiElement, target: PsiElement): Boolean = {
    val Target = target

    scope.breadthFirst.exists {
      case ScAssignStmt(left, _) => true
      case e@ScInfixExpr(Resolved(Target, _), _, _) if e.isAssignmentOperator => true
      case _ => false
    }
  }
}