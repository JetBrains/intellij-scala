package org.jetbrains.plugins.scala.lang.transformation.general

import com.intellij.psi.PsiElement
import org.jetbrains.plugins.scala.lang.psi.api.expr._
import org.jetbrains.plugins.scala.lang.transformation.AbstractTransformer
import org.jetbrains.plugins.scala.project.ProjectContext

class ExpandForComprehension extends AbstractTransformer {

  override protected def transformation(implicit project: ProjectContext): PartialFunction[PsiElement, Unit] = {
    case scFor: ScFor =>
      val desugared = getDesugaredElement(scFor)
      desugared.foreach(scFor.replace)
  }

  private def getDesugaredElement(scFor: ScFor): Option[PsiElement] =
    scFor.desugared(forDisplay = true)

  override def needsReformat(e: PsiElement): Boolean = e match {
    case e: ScFor =>
      e.getText.contains("\n")
    case _ => false
  }
}
