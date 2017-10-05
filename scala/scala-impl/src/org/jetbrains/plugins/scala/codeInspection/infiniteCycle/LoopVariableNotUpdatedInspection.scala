package org.jetbrains.plugins.scala
package codeInspection.infiniteCycle

import com.intellij.codeInspection.{ProblemHighlightType, ProblemsHolder}
import com.intellij.psi.{PsiElement, PsiReference}
import org.jetbrains.plugins.scala.codeInspection.AbstractInspection
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiElement
import org.jetbrains.plugins.scala.lang.psi.api.expr._
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScVariable

/**
 * Pavel Fatin
 */

class LoopVariableNotUpdatedInspection extends AbstractInspection("LoopVariableNotUpdatedInspection", "Loop variable not updated inside loop") {
  private val ComparisonOperators = Set("==", "!=", ">", "<", ">=", "<=")

  override def actionFor(implicit holder: ProblemsHolder): PartialFunction[PsiElement, Unit] = {
    case ScWhileStmt(
      Some(ScInfixExpr((ref: ScReferenceExpression) && (ResolvesTo(target@Parent(Parent(_: ScVariable)))), ElementText(operator), _)),
      Some(body)) if !ref.isQualified && ComparisonOperators.contains(operator) && !isMutatedWithing(body, target) =>
        holder.registerProblem(ref.asInstanceOf[PsiReference],
          getDisplayName, ProblemHighlightType.GENERIC_ERROR_OR_WARNING)
  }

  private def isMutatedWithing(scope: ScalaPsiElement, target: PsiElement): Boolean = {
    val Target = target

    scope.breadthFirst().exists {
      case ScAssignStmt(_, _) => true
      case e@ScInfixExpr(ResolvesTo(Target), _, _) if e.isAssignmentOperator => true
      case _ => false
    }
  }
}
