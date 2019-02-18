package org.jetbrains.plugins.scala.lang.transformation

import com.intellij.psi.PsiElement
import org.jetbrains.plugins.scala.project.ProjectContext

/**
  * @author Pavel Fatin
  */
abstract class AbstractTransformer extends Transformer {
  protected final override def transform(e: PsiElement): Boolean =
    transformation(e.getProject).lift(e).isDefined

  final override def isApplicableTo(e: PsiElement): Boolean =
    transformation(e.getProject).isDefinedAt(e)

  protected def transformation(implicit project: ProjectContext): PartialFunction[PsiElement, Unit]
}
