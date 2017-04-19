package org.jetbrains.plugins.scala.lang.transformation

import com.intellij.psi.PsiElement
import org.jetbrains.plugins.scala.project.ProjectContext

/**
  * @author Pavel Fatin
  */
abstract class AbstractTransformer extends Transformer {
  def transform(e: PsiElement): Boolean =
    transformation(e.getProject).lift(e).nonEmpty

  def transformation(implicit project: ProjectContext): PartialFunction[PsiElement, Unit]
}
