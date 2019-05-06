package org.jetbrains.plugins.scala
package lang
package psi
package impl

import com.intellij.lang.Language
import com.intellij.openapi.fileTypes.FileType
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.{FileViewProviderFactory, PsiManager, SingleRootFileViewProvider}

final class ScFileViewProviderFactory extends FileViewProviderFactory {

  override def createFileViewProvider(file: VirtualFile,
                                      language: Language,
                                      manager: PsiManager,
                                      eventSystemEnabled: Boolean) =
    new ScFileViewProviderFactory.ScFileViewProvider(file, eventSystemEnabled)(manager)
}

object ScFileViewProviderFactory {

  class ScFileViewProvider(file: VirtualFile, eventSystemEnabled: Boolean)
                          (implicit manager: PsiManager)
    extends SingleRootFileViewProvider(manager, file, eventSystemEnabled, ScalaLanguage.INSTANCE) {

    override final def createFile(project: Project,
                                  file: VirtualFile,
                                  fileType: FileType): ScalaFileImpl =
      createFile(getBaseLanguage)

    override def createFile(language: Language): ScalaFileImpl =
      super.createFile(language).asInstanceOf[ScalaFileImpl]

    override def createCopy(copy: VirtualFile): ScFileViewProvider =
      new ScFileViewProvider(copy, eventSystemEnabled = false)
  }

}