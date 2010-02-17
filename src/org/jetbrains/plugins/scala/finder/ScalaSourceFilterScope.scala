package org.jetbrains.plugins.scala.finder

import org.jetbrains.plugins.scala.ScalaFileType
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.openapi.project.Project
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.fileTypes.{FileType, StdFileTypes}
import com.intellij.openapi.module.Module

/**
 * User: Alexander Podkhalyuzin
 * Date: 17.02.2010
 */

class ScalaSourceFilterScope(myDelegate: GlobalSearchScope, project: Project) extends GlobalSearchScope(project) {
  val myIndex = ProjectRootManager.getInstance(project).getFileIndex

  def isSearchInLibraries: Boolean = {
    return null == myDelegate || myDelegate.isSearchInLibraries
  }

  def compare(file1: VirtualFile, file2: VirtualFile): Int = {
    return if (null != myDelegate) myDelegate.compare(file1, file2) else 0
  }

  def isSearchInModuleContent(aModule: Module): Boolean = {
    return null == myDelegate || myDelegate.isSearchInModuleContent(aModule)
  }

  def contains(file: VirtualFile): Boolean = {
    val fileType: FileType = file.getFileType
    return (null == myDelegate || myDelegate.contains(file)) && (ScalaFileType.SCALA_FILE_TYPE == fileType &&
            myIndex.isInSourceContent(file) || StdFileTypes.CLASS == fileType && myIndex.isInLibraryClasses(file))
  }
}