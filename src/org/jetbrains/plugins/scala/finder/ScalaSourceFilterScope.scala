package org.jetbrains.plugins.scala.finder

import com.intellij.openapi.fileTypes.{FileTypeManager, StdFileTypes}
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.search.GlobalSearchScope
import org.jetbrains.plugins.scala.ScalaFileType
import org.jetbrains.plugins.scala.util.ScalaLanguageDerivative

/**
 * User: Alexander Podkhalyuzin
 * Date: 17.02.2010
 */
class ScalaSourceFilterScope(myDelegate: GlobalSearchScope, project: Project) extends GlobalSearchScope(project) {
  val myIndex = ProjectRootManager.getInstance(project).getFileIndex

  def isSearchInLibraries: Boolean = {
    null == myDelegate || myDelegate.isSearchInLibraries
  }

  def compare(file1: VirtualFile, file2: VirtualFile): Int = {
    if (null != myDelegate) myDelegate.compare(file1, file2) else 0
  }

  def isSearchInModuleContent(aModule: Module): Boolean = {
    null == myDelegate || myDelegate.isSearchInModuleContent(aModule)
  }

  def contains(file: VirtualFile): Boolean = {
    (null == myDelegate || myDelegate.contains(file)) && (
      (FileTypeManager.getInstance().isFileOfType(file, ScalaFileType.SCALA_FILE_TYPE) ||
        ScalaLanguageDerivative.hasDerivativeForFileType(file.getFileType)) && myIndex.isInSourceContent(file) ||
        StdFileTypes.CLASS.getDefaultExtension == file.getExtension && myIndex.isInLibraryClasses(file))
  }
}