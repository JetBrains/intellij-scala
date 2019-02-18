package org.jetbrains.plugins.scala.lang.transformation
package calls

import com.intellij.psi.PsiElement
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.psi.api.ScalaPsiElement
import org.jetbrains.plugins.scala.lang.psi.api.expr._
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaCode._
import org.jetbrains.plugins.scala.project.ProjectContext

/**
  * @author Pavel Fatin
  */
class ExpandDynamicCall extends AbstractTransformer {
  protected def transformation(implicit project: ProjectContext): PartialFunction[PsiElement, Unit] = {
    case ScMethodCall(r @ RenamedReference(id, "applyDynamic"), _) =>
      r.replace(code"${r.qualifier.get}.applyDynamic(${quote(id)})")

    case e @ ScInfixExpr(l, RenamedReference(id, "applyDynamic"), r) =>
      e.replace(code"$l.applyDynamic(${quote(id)})($r)")

    case e @ ScMethodCall(r @ RenamedReference(id, "applyDynamicNamed"), assignments) =>
      e.replace(code"${r.qualifier.get}.applyDynamicNamed(${quote(id)})(${@@(assignments.map(asTuple))})")

    case e @ ScInfixExpr(l, RenamedReference(id, "applyDynamicNamed"), r) =>
      val assignments = r.breadthFirst().filter(_.isInstanceOf[ScAssignment]).toVector
      e.replace(code"$l.applyDynamicNamed(${quote(id)})(${@@(assignments.map(asTuple))})")

    case (e: ScReferenceExpression) && (r @ RenamedReference(id, "selectDynamic")) if r.qualifier.isDefined =>
      e.replace(code"${r.qualifier.get}.selectDynamic(${quote(id)})")

    case e @ ScPostfixExpr(l, RenamedReference(id, "selectDynamic")) =>
      e.replace(code"$l.selectDynamic(${quote(id)})")

    case e @ ScAssignment(l @ RenamedReference(id, "updateDynamic"), Some(r)) =>
      e.replace(code"${l.qualifier.get}.updateDynamic(${quote(id)})($r)")

    case e @ ScAssignment(ScPostfixExpr(l, RenamedReference(id, "updateDynamic")), Some(r)) =>
      e.replace(code"$l.updateDynamic(${quote(id)})($r)")
  }

  private def asTuple(assignment: PsiElement)(implicit project: ProjectContext): ScalaPsiElement = assignment match {
    case ScAssignment(l, Some(r)) => code"(${quote(l.getText)}, $r)"
  }
}
