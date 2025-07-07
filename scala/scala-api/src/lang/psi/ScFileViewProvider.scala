package org.jetbrains.plugins.scala
package lang.psi

import com.intellij.lang.Language
import com.intellij.openapi.fileTypes.{FileType, PlainTextLanguage}
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.{PsiFile, PsiManager, SingleRootFileViewProvider}

final class ScFileViewProvider(
  manager: PsiManager,
  file: VirtualFile,
  eventSystemEnabled: Boolean,
  language: Language
) extends SingleRootFileViewProvider(
  manager,
  file,
  eventSystemEnabled,
  ScFileViewProvider.calcBaseLanguage(file, language)
) {

  override def createFile(
    project: Project,
    file: VirtualFile,
    fileType: FileType
  ): PsiFile =
    createFile(getBaseLanguage)

  override def createCopy(copy: VirtualFile) =
    new ScFileViewProvider(getManager, copy, eventSystemEnabled = false, getBaseLanguage)
}

object ScFileViewProvider {

  /**
   * This is a partial copy of the method [[com.intellij.psi.SingleRootFileViewProvider#calcBaseLanguage]]
   *
   * I haven't copied the full logic, only the part required for SCL-24093
   *
   * @see [[com.intellij.psi.impl.PsiFileFactoryImpl#trySetupPsiForFile]]
   */
  private def calcBaseLanguage(file: VirtualFile, language: Language): Language = {
    if (SingleRootFileViewProvider.isTooLargeForIntelligence(file))
      PlainTextLanguage.INSTANCE
    else
      language
  }
}
