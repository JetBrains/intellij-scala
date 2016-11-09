package org.jetbrains.plugins.scala.finder

import com.intellij.openapi.fileTypes.{FileTypeManager, StdFileTypes}
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.search.{GlobalSearchScope, LocalSearchScope, SearchScope}
import org.jetbrains.plugins.scala.util.ScalaLanguageDerivative
import org.jetbrains.plugins.scala.{ScalaFileType, ScalaLanguage}

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
      (FileTypeManager.getInstance().isFileOfType(file, ScalaFileType.INSTANCE) ||
        ScalaLanguageDerivative.hasDerivativeForFileType(file.getFileType)) && myIndex.isInSourceContent(file) ||
        StdFileTypes.CLASS.getDefaultExtension == file.getExtension && myIndex.isInLibraryClasses(file))
  }
}

class SourceFilterScope(myDelegate: GlobalSearchScope, project: Project) extends GlobalSearchScope(project) {
  val myIndex = ProjectRootManager.getInstance(project).getFileIndex

  override def contains(file: VirtualFile): Boolean = {
    (myDelegate == null || myDelegate.contains(file)) && myIndex.isInSourceContent(file)
  }

  override def compare(file1: VirtualFile, file2: VirtualFile): Int = {
    if (myDelegate == null) myDelegate.compare(file1, file2) else 0
  }

  override def isSearchInModuleContent(aModule: Module): Boolean = {
    myDelegate == null || myDelegate.isSearchInModuleContent(aModule)
  }

  override def isSearchInLibraries: Boolean = {
    myDelegate == null || myDelegate.isSearchInLibraries
  }
}

object ScalaSourceFilterScope {
  def apply(scope: SearchScope, project: Project): SearchScope = scope match {
    case global: GlobalSearchScope => new ScalaSourceFilterScope(global, project)
    case local: LocalSearchScope =>
      val filtered = local.getScope.filter(_.getLanguage.isKindOf(ScalaLanguage.INSTANCE))
      val displayName = local.getDisplayName + " in scala"
      new LocalSearchScope(filtered, displayName, local.isIgnoreInjectedPsi)
    case _ => scope
  }
}