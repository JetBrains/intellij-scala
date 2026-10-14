package org.jetbrains.plugins.scala.project

import com.intellij.psi.PsiFile

/** [[ScalaFileFeatures]] from the project model: the file's module and [[ScalaFeaturePusher]] */
final class ModuleScalaFileFeatures extends ScalaFileFeatures {

  override def moduleFeatures(file: PsiFile): Option[ScalaFeatures] = file.module.map(_.features)

  override def fileFeatures(file: PsiFile): Option[ScalaFeatures] = ScalaFeaturePusher.getFeatures(file)

  override def isCompilerStrictMode(file: PsiFile): Boolean = file.isCompilerStrictMode
}
