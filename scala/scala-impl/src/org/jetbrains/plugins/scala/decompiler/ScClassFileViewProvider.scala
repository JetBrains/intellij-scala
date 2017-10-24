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
  extends SingleRootFileViewProvider(manager, file, physical, ScalaFileType.INSTANCE.getLanguage) {

  override def getContents: CharSequence =
    if (!isScalaFile) ""
    else DecompilerUtil.decompile(getVirtualFile, getVirtualFile.contentsToByteArray).sourceText.replace("\r", "")

  override def createFile(project: Project, vFile: VirtualFile, fileType: FileType): PsiFile = {
    if (!isScalaFile) null
    else {
      val file = new ScalaFileImpl(this)
      val adj = file.asInstanceOf[CompiledFileAdjuster]
      adj.setCompiled(c = true)
      adj.setVirtualFile(vFile)
      file
    }
  }

  override def createCopy(copy: VirtualFile): SingleRootFileViewProvider =
    new ScClassFileViewProvider(getManager, copy, false, isScalaFile)

}