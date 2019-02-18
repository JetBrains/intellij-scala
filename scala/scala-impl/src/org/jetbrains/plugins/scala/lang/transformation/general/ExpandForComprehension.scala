package org.jetbrains.plugins.scala.lang.transformation
package general

import com.intellij.psi.PsiElement
import org.jetbrains.plugins.scala.lang.psi.api.expr._
import org.jetbrains.plugins.scala.project.ProjectContext

/**
  * @author Pavel Fatin
  */
class ExpandForComprehension extends AbstractTransformer {
  protected def transformation(implicit project: ProjectContext): PartialFunction[PsiElement, Unit] = {
    case e: ScFor =>
      desugared(e).foreach(e.replace)
  }

  private def desugared(e: ScFor)(implicit project: ProjectContext): Option[PsiElement] =
    e.desugared(forDisplay = true)
}
