package org.jetbrains.plugins.scala
package codeInspection.infiniteCycle

import com.intellij.codeInspection.{ProblemHighlightType, ProblemsHolder}
import com.intellij.psi.{PsiElement, PsiReference}
import org.jetbrains.plugins.scala.codeInspection.{AbstractInspection, ScalaInspectionBundle}
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.psi.api.ScalaPsiElement
import org.jetbrains.plugins.scala.lang.psi.api.expr._
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScVariable

import scala.annotation.nowarn

/**
 * Pavel Fatin
 */
@nowarn("msg=" + AbstractInspection.DeprecationText)
class LoopVariableNotUpdatedInspection extends AbstractInspection(ScalaInspectionBundle.message("display.name.loop.variable.not.updated.inside.loop")) {
  private val ComparisonOperators = Set("==", "!=", ">", "<", ">=", "<=")

  override def actionFor(implicit holder: ProblemsHolder, isOnTheFly: Boolean): PartialFunction[PsiElement, Unit] = {
    case ScWhile(
      Some(ScInfixExpr((ref: ScReferenceExpression) && (ResolvesTo(target@Parent(Parent(_: ScVariable)))), ElementText(operator), _)),
      Some(body)) if !ref.isQualified && ComparisonOperators.contains(operator) && !isMutatedWithing(body, target) =>
        holder.registerProblem(ref.asInstanceOf[PsiReference],
          getDisplayName, ProblemHighlightType.GENERIC_ERROR_OR_WARNING)
  }

  private def isMutatedWithing(scope: ScalaPsiElement, target: PsiElement): Boolean = {
    val Target = target

    scope.breadthFirst().exists {
      case ScAssignment(_, _) => true
      case e@ScInfixExpr(ResolvesTo(Target), _, _) if e.isAssignmentOperator => true
      case _ => false
    }
  }
}
