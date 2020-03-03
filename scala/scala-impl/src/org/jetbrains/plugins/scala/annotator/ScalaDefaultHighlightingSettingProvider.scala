package org.jetbrains.plugins.scala.annotator

import com.intellij.codeInsight.daemon.impl.analysis.{DefaultHighlightingSettingProvider, FileHighlightingSetting}
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiManager
import org.jetbrains.plugins.scala.extensions.PsiFileExt

class ScalaDefaultHighlightingSettingProvider
  extends DefaultHighlightingSettingProvider {

  override def getDefaultSetting(project: Project, file: VirtualFile): FileHighlightingSetting = {
    val isScala3 = Option(PsiManager.getInstance(project).findFile(file)).exists(_.isScala3File)
    if (isScala3)
      FileHighlightingSetting.SKIP_INSPECTION
    else
      null
  }
}
