package org.jetbrains.plugins.scala.projectView

import com.intellij.ide.IconProvider
import com.intellij.openapi.progress.ProgressManager
import com.intellij.psi._
import org.jetbrains.plugins.scala.lang.psi.api.ScalaFile

import javax.swing.Icon

final class ScalaIconProvider extends IconProvider {

  override def getIcon(element: PsiElement, flags: Int): Icon = element match {
    case file: ScalaFile =>
      ProgressManager.checkCanceled()
      Node(file)(null, null).getIcon(flags)
    case _ => null
  }
}