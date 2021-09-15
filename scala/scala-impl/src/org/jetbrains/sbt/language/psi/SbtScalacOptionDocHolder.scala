package org.jetbrains.sbt.language.psi

import com.intellij.openapi.project.Project
import com.intellij.psi.PsiManager
import com.intellij.psi.impl.light.LightElement
import org.jetbrains.plugins.scala.ScalaLanguage
import org.jetbrains.sbt.language.utils.SbtScalacOptionInfo

final case class SbtScalacOptionDocHolder(option: SbtScalacOptionInfo)(implicit project: Project)
  extends LightElement(PsiManager.getInstance(project), ScalaLanguage.INSTANCE) {
  override def getText: String = option.flag
  override def toString: String = s"SbtScalacOptionDocHolder(${option.flag})"
}
