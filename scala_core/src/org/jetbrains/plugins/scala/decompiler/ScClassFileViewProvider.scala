package org.jetbrains.plugins.scala.decompiler


import com.intellij.lang.Language
import com.intellij.openapi.fileTypes.FileType
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.{SingleRootFileViewProvider, PsiManager, PsiFile}
import com.intellij.testFramework.LightVirtualFile

import java.io.ByteArrayOutputStream
import lang.psi.ScalaFileImpl
import scalax.rules.scalasig.{ClassFileParser, ScalaSigAttributeParsers, ScalaSigPrinter, ByteCode}

/**
 * @author ilyas
 */

class ScClassFileViewProvider(manager: PsiManager, file: VirtualFile, physical: Boolean)
extends SingleRootFileViewProvider(manager, file, physical) {

  def this(manager: PsiManager, file: VirtualFile) = this(manager, file, true);

  override def creatFile(project: Project, vFile: VirtualFile, fileType: FileType): PsiFile = {
    if (ProjectRootManager.getInstance(project).getFileIndex().isInLibraryClasses(vFile)) {
      val name = vFile.getNameWithoutExtension
      // skip inners & anonymous
      if (name.lastIndexOf('$') >= 0) null else new ScalaFileImpl(this)
    }
    else null
  }

  override def getBaseLanguage = ScalaFileType.SCALA_FILE_TYPE.getLanguage

  override def createCopy(copy: LightVirtualFile): SingleRootFileViewProvider =
    new ScClassFileViewProvider(getManager, copy, false)
}