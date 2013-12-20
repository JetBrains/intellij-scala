package org.jetbrains.plugins.scala
package decompiler


import com.intellij.openapi.fileTypes.FileType
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.{SingleRootFileViewProvider, PsiManager, PsiFile}
import lang.psi.impl.ScalaFileImpl
import com.intellij.psi.impl.compiled.ClassFileStubBuilder

/**
 * @author ilyas
 */

class ScClassFileViewProvider(manager: PsiManager, file: VirtualFile, physical: Boolean)
extends SingleRootFileViewProvider(manager, file, physical) {

  def this(manager: PsiManager, file: VirtualFile) = this(manager, file, true)

  override def createFile(project: Project, vFile: VirtualFile, fileType: FileType): PsiFile = {
    val builder = new ClassFileStubBuilder
    // skip inners & anonymous
    if (!builder.acceptsFile(vFile)) null
    else {
      val file = new ScalaFileImpl(this)
      val adj = file.asInstanceOf[CompiledFileAdjuster]
      adj.setCompiled(true)
      adj.setVirtualFile(vFile)
      file
    }
  }

  override def getBaseLanguage = ScalaFileType.SCALA_FILE_TYPE.getLanguage

  override def createCopy(copy: VirtualFile): SingleRootFileViewProvider =
    new ScClassFileViewProvider(getManager, copy, false)
}