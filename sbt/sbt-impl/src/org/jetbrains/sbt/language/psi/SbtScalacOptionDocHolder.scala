package org.jetbrains.sbt.language.psi

import com.intellij.openapi.project.Project
import com.intellij.psi.PsiManager
import com.intellij.psi.impl.light.LightElement
import org.jetbrains.plugins.scala.ScalaLanguage
import org.jetbrains.sbt.language.utils.SbtScalacOptionInfo

final class SbtScalacOptionDocHolder(val options: Seq[SbtScalacOptionInfo])(implicit project: Project)
  extends LightElement(PsiManager.getInstance(project), ScalaLanguage.INSTANCE) {
  assert(options.nonEmpty)

  def this(option: SbtScalacOptionInfo)(implicit project: Project) = this(Seq(option))

  override def getText: String = options.head.flag

  override def toString: String = s"SbtScalacOptionDocHolder($getText)"
}
