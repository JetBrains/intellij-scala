package org.jetbrains.plugins.scala
package decompiler

import com.intellij.openapi.fileTypes.FileType
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.{PsiFile, PsiManager, SingleRootFileViewProvider}
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaFileImpl

/**
 * @author ilyas
 */
class ScClassFileViewProvider(manager: PsiManager, file: VirtualFile, physical: Boolean, isScalaFile: Boolean)
  extends SingleRootFileViewProvider(manager, file, physical, ScalaLanguage.INSTANCE) {

  override def getContents: CharSequence =
    if (!isScalaFile) ""
    else DecompilerUtil.decompile(getVirtualFile).sourceText

  override def createFile(project: Project, virtualFile: VirtualFile, fileType: FileType): PsiFile = {
    if (!isScalaFile) null
    else {
      val file = new ScalaFileImpl(this)
      file.isCompiled = true
      file.virtualFile = virtualFile
      file
    }
  }

  override def createCopy(copy: VirtualFile): SingleRootFileViewProvider =
    new ScClassFileViewProvider(getManager, copy, false, isScalaFile)

}