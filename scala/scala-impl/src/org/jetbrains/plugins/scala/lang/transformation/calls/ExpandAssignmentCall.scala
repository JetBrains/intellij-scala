package org.jetbrains.plugins.scala.lang.transformation
package calls

import com.intellij.psi.PsiElement
import org.jetbrains.plugins.scala.extensions.{ElementName, ReferenceTarget}
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScInfixExpr
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaCode._
import org.jetbrains.plugins.scala.project.ProjectContext

/**
  * @author Pavel Fatin
  */
class ExpandAssignmentCall extends AbstractTransformer {
  protected def transformation(implicit project: ProjectContext): PartialFunction[PsiElement, Unit] = {
    case e@ScInfixExpr(l, o@ReferenceTarget(ElementName(name)), r) if o.getText == name + "=" =>
      val (a, b) = if (name.endsWith(":")) (r, l) else (l, r)
      e.replace(code"$l = $a $name $b")
  }
}
