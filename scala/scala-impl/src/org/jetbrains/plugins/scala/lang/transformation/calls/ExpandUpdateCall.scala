package org.jetbrains.plugins.scala.lang.transformation
package calls

import com.intellij.psi.PsiElement
import org.jetbrains.plugins.scala.lang.psi.api.expr.{ScAssignment, ScMethodCall}
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaCode._
import org.jetbrains.plugins.scala.project.ProjectContext

/**
  * @author Pavel Fatin
  */
class ExpandUpdateCall extends AbstractTransformer {
  protected def transformation(implicit project: ProjectContext): PartialFunction[PsiElement, Unit] = {
    case e @ ScAssignment(ScMethodCall(r @ RenamedReference(_, "update"), keys), Some(value)) =>
      e.replace(code"$r.update(${@@(keys :+ value)})")
  }
}
