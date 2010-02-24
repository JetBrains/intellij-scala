package org.jetbrains.plugins.scala
package decompiler


import com.intellij.lang.Language
import com.intellij.openapi.fileTypes.FileType
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.{SingleRootFileViewProvider, PsiManager, PsiFile}
import com.intellij.testFramework.LightVirtualFile

import java.io.ByteArrayOutputStream
import lang.psi.impl.ScalaFileImpl
import com.intellij.psi.impl.compiled.ClassFileStubBuilder

/**
 * @author ilyas
 */

class ScClassFileViewProvider(manager: PsiManager, file: VirtualFile, physical: Boolean)
extends SingleRootFileViewProvider(manager, file, physical) {

  def this(manager: PsiManager, file: VirtualFile) = this(manager, file, true);

  override def createFile(project: Project, vFile: VirtualFile, fileType: FileType): PsiFile = {
    val name = vFile.getNameWithoutExtension
    val builder = new ClassFileStubBuilder
    // skip inners & anonymous
    if (!builder.acceptsFile(vFile)) null
    else {
      val file = new ScalaFileImpl(this)
      val adj = file.asInstanceOf[CompiledFileAdjuster]
      adj.setCompiled(true)
      adj.setSourceFileName(DecompilerUtil.decompile(vFile.contentsToByteArray, vFile)._2)
      file
    }
  }

  override def getBaseLanguage = ScalaFileType.SCALA_FILE_TYPE.getLanguage

  override def createCopy(copy: LightVirtualFile): SingleRootFileViewProvider =
    new ScClassFileViewProvider(getManager, copy, false)
}