package org.jetbrains.plugins.scala.project

import com.intellij.psi.{PsiElement, PsiFile}
import org.jetbrains.annotations.Nullable
import org.jetbrains.plugins.scala.extensions.PsiFileExt
import org.jetbrains.plugins.scala.project.ScalaFeatures.PsiContextFeatures

object ScalaFeaturesProvider {
  private def forPsi(psi: PsiElement): Option[ScalaFeatures] =
    Option(psi.getContainingFile).map(forFile)

  /** Resolve parser features from attached synthetic-file data, module settings, or a deterministic language fallback. */
  private def forFile(file: PsiFile): ScalaFeatures = {
    val attached =
      if (!file.isPhysical) ScalaFeatures.getAttachedScalaFeatures(file)
      else None

    attached
      .orElse(file.module.map(_.features))
      .getOrElse(if (file.isScala3File) ScalaFeatures.defaultScala3 else ScalaFeatures.default)
  }

  def forPsiOrDefault(@Nullable psi: PsiElement): ScalaFeatures = {
    val delegate =
      if (psi eq null) ScalaFeatures.default
      else forPsi(psi).getOrElse(ScalaFeatures.default)

    PsiContextFeatures(psi, delegate)
  }
}
