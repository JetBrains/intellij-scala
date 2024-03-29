package org.jetbrains.plugins.scala.lang.transformation.calls

import com.intellij.psi.PsiElement
import org.jetbrains.plugins.scala.extensions.{ElementName, ReferenceTarget}
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScInfixExpr
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaCode._
import org.jetbrains.plugins.scala.lang.transformation.AbstractTransformer
import org.jetbrains.plugins.scala.project.ProjectContext

class ExpandAssignmentCall extends AbstractTransformer {
  override protected def transformation(implicit project: ProjectContext): PartialFunction[PsiElement, Unit] = {
    case e@ScInfixExpr(l, o@ReferenceTarget(ElementName(name)), r) if o.textMatches(name + "=") =>
      val (a, b) = if (name.endsWith(":")) (r, l) else (l, r)
      e.replace(code"$l = $a $name $b")
  }
}
