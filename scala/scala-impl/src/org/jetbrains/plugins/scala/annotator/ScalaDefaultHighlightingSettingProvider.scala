package org.jetbrains.plugins.scala.annotator

import com.intellij.codeInsight.daemon.impl.analysis.{DefaultHighlightingSettingProvider, FileHighlightingSetting}
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.{PsiJavaFile, PsiManager}
import org.jetbrains.plugins.scala.lang.psi.api.ScalaFile
import org.jetbrains.plugins.scala.project.ProjectPsiElementExt

class ScalaDefaultHighlightingSettingProvider
  extends DefaultHighlightingSettingProvider {

  override def getDefaultSetting(project: Project, file: VirtualFile): FileHighlightingSetting = {
    val isJavaOrScalaInScala3Module = Option(PsiManager.getInstance(project).findFile(file)).exists {
      case psiFile@(_: ScalaFile | _: PsiJavaFile) => psiFile.isInScala3Module
      case _ => false  
    }
    if (isJavaOrScalaInScala3Module)
      FileHighlightingSetting.SKIP_INSPECTION
    else
      null
  }
}
