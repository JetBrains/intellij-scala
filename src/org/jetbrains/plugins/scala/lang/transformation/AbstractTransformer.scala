package org.jetbrains.plugins.scala.lang.transformation

import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement

/**
  * @author Pavel Fatin
  */
abstract class AbstractTransformer extends Transformer {
  protected implicit var project: Project = _

  def transform(e: PsiElement): Boolean = {
    project = e.getProject
    transformation.lift(e).nonEmpty
  }

  protected def transformation: PartialFunction[PsiElement, Unit]
}
