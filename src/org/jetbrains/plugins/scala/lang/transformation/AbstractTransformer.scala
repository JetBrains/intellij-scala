package org.jetbrains.plugins.scala.lang.transformation

import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement

/**
  * @author Pavel Fatin
  */
abstract class AbstractTransformer extends Transformer {
  def transform(e: PsiElement): Boolean =
    transformation(e.getProject).lift(e).nonEmpty

  def transformation(implicit project: Project): PartialFunction[PsiElement, Unit]
}
