package org.jetbrains.plugins.scala.decompiler

import com.intellij.openapi.fileTypes.FileType
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.impl.PsiManagerImpl
import com.intellij.psi.{SingleRootFileViewProvider, PsiManager, PsiFile}
import com.intellij.testFramework.LightVirtualFile
import lang.psi.impl.compiled.ScClsFileImpl

/**
 * @author ilyas
 */

class ScClassFileViewProvider(manager: PsiManager, file: VirtualFile, physical: Boolean)
extends SingleRootFileViewProvider(manager, file, physical) {

  def this(manager: PsiManager, file: VirtualFile) = this (manager, file, true);

  override def creatFile(project: Project, vFile: VirtualFile, fileType: FileType): PsiFile = {
    if (ProjectRootManager.getInstance(project).getFileIndex().isInLibraryClasses(vFile)) {
      val name = vFile.getName

      // skip inners & anonymous
      var dotIndex = name.lastIndexOf('.')
      if (dotIndex < 0) dotIndex = name.length
      val index = name.lastIndexOf('$', dotIndex)
      if (index >= 0) return null
      return new ScClsFileImpl(PsiManager.getInstance(project).asInstanceOf[PsiManagerImpl], this)
    }
    return null
  }

  override def createCopy(copy: LightVirtualFile): SingleRootFileViewProvider =
    new ScClassFileViewProvider(getManager, copy, false)
}