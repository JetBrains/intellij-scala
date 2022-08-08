package org.jetbrains.plugins.scala
package lang.psi

import com.intellij.lang.Language
import com.intellij.openapi.fileTypes.FileType
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.{PsiFile, PsiManager, SingleRootFileViewProvider}

final class ScFileViewProvider(manager: PsiManager, file: VirtualFile,
                               eventSystemEnabled: Boolean, language: Language)
  extends SingleRootFileViewProvider(manager, file, eventSystemEnabled, language) {

  override def createFile(project: Project,
                          file: VirtualFile,
                          fileType: FileType): PsiFile =
    createFile(getBaseLanguage)

  override def createCopy(copy: VirtualFile) =
    new ScFileViewProvider(getManager, copy, eventSystemEnabled = false, getBaseLanguage)
}
