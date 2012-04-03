package org.jetbrains.plugins.scala
package codeInspection.infiniteCycle

import codeInspection.AbstractInspection
import lang.psi.api.statements.{ScVariable, ScPatternDefinition}
import com.intellij.codeInspection.{ProblemHighlightType, ProblemsHolder}
import com.intellij.psi.{PsiReference, PsiElement}
import extensions._
import lang.psi.ScalaPsiElement
import lang.psi.api.expr._

/**
 * Pavel Fatin
 */

class InfiniteLoopInspection extends AbstractInspection("InfiniteLoopInspection", "Infinete Loop Inspection") {
  private val ComparisonOperators = Set("==", "!=", ">", "<", ">=", "<=")

  def actionFor(holder: ProblemsHolder) = {
    case ScWhileStmt(
    Some(ScInfixExpr((ref: ScReferenceExpression) && (Resolved(target@Parent(Parent(entity)), _)), ElementText(operator), _)),
    Some(body)) if !ref.isQualified && ComparisonOperators.contains(operator) =>
      entity match {
        case _: ScPatternDefinition =>
          holder.registerProblem(ref.asInstanceOf[PsiReference],
            "Loop on value", ProblemHighlightType.GENERIC_ERROR_OR_WARNING)
        case _: ScVariable if !isMutatedWithing(body, target) =>
          holder.registerProblem(ref.asInstanceOf[PsiReference],
            "Loop variable is (probably) not mutated", ProblemHighlightType.GENERIC_ERROR_OR_WARNING)
        case _ => // do nothing
      }
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