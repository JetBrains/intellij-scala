package org.jetbrains.plugins.scala
package lang
package psi

import com.intellij.lang.Language
import com.intellij.openapi.fileTypes.FileType
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.{PsiManager, SingleRootFileViewProvider}
import org.jetbrains.plugins.scala.lang.psi.api.ScFile

class ScFileViewProvider(file: VirtualFile, eventSystemEnabled: Boolean)
                        (implicit manager: PsiManager)
  extends SingleRootFileViewProvider(manager, file, eventSystemEnabled, ScalaLanguage.INSTANCE) {

  override final def createFile(project: Project,
                                file: VirtualFile,
                                fileType: FileType): ScFile =
    createFile(getBaseLanguage)

  override def createFile(language: Language): ScFile =
    super.createFile(language).asInstanceOf[ScFile]

  override def createCopy(copy: VirtualFile): ScFileViewProvider =
    new ScFileViewProvider(copy, eventSystemEnabled = false)
}
