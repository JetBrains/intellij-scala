package org.jetbrains.plugins.scala.util

import com.intellij.openapi.fileTypes.FileTypeManager
import com.intellij.openapi.project.Project
import com.intellij.psi.{PsiFileFactory, PsiFile}
import com.intellij.util.LocalTimeCounter

/**
 * User: Alexander Podkhalyuzin
 * Date: 06.10.2008
 */

object ScalaTestUtils {
  def createPseudoPhysicalScalaFile(project: Project, text: String): PsiFile = {
    val TEMP_FILE = project.getProjectFilePath() + "temp.scala";
    return PsiFileFactory.getInstance(project).createFileFromText(
        TEMP_FILE,
        FileTypeManager.getInstance().getFileTypeByFileName(TEMP_FILE),
        text,
        LocalTimeCounter.currentTime(),
        true)
  }
}