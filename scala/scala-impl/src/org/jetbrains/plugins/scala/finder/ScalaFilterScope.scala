package org.jetbrains.plugins.scala.finder

import com.intellij.ide.highlighter.JavaFileType
import com.intellij.openapi.fileTypes.{FileTypeManager, StdFileTypes}
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.{ProjectFileIndex, ProjectRootManager}
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.search.GlobalSearchScope.{getScopeRestrictedByFileTypes, projectScope}
import com.intellij.psi.search.searches.{MethodReferencesSearch, ReferencesSearch}
import com.intellij.psi.search.{GlobalSearchScope, LocalSearchScope, SearchScope}
import org.jetbrains.plugins.scala.lang.psi.ElementScope
import org.jetbrains.plugins.scala.util.ScalaLanguageDerivative
import org.jetbrains.plugins.scala.{ScalaFileType, ScalaLanguage}

/**
  * User: Alexander Podkhalyuzin
  * Date: 17.02.2010
  */
abstract class FilterScope protected(elementScope: ElementScope) extends GlobalSearchScope(elementScope.project) {
  def this(scope: GlobalSearchScope, project: Project) =
    this(ElementScope(project, scope))

  private val myDelegate = elementScope.scope

  protected val myIndex: ProjectFileIndex = ProjectRootManager.getInstance(elementScope.project).getFileIndex

  protected def isValid(file: VirtualFile): Boolean

  override def contains(file: VirtualFile): Boolean =
    (myDelegate == null || myDelegate.contains(file)) && isValid(file)

  override def compare(file1: VirtualFile, file2: VirtualFile): Int =
    if (myDelegate != null) myDelegate.compare(file1, file2) else 0

  override def isSearchInModuleContent(aModule: Module): Boolean =
    myDelegate == null || myDelegate.isSearchInModuleContent(aModule)

  override def isSearchInLibraries: Boolean =
    myDelegate == null || myDelegate.isSearchInLibraries
}

class ScalaFilterScope(scope: GlobalSearchScope, project: Project) extends FilterScope(scope, project) {
  override protected def isValid(file: VirtualFile): Boolean = {
    val isScalaSource = myIndex.isInSourceContent(file) &&
      (FileTypeManager.getInstance().isFileOfType(file, ScalaFileType.INSTANCE) ||
      ScalaLanguageDerivative.hasDerivativeForFileType(file.getFileType))

    val isCompiled =
      StdFileTypes.CLASS.getDefaultExtension == file.getExtension &&
        myIndex.isInLibraryClasses(file)

    isScalaSource || isCompiled
  }
}

object ScalaFilterScope {
  def apply(search: ReferencesSearch.SearchParameters): SearchScope =
    updateScope(search.getProject)(search.getEffectiveSearchScope)

  def apply(search: MethodReferencesSearch.SearchParameters): SearchScope =
    updateScope(search.getProject)(search.getEffectiveSearchScope)

  def apply(project: Project, scope: SearchScope): SearchScope = updateScope(project)(scope)

  def apply(project: Project, scope: GlobalSearchScope): GlobalSearchScope = new ScalaFilterScope(scope, project)

  private def updateScope(project: Project): SearchScope => SearchScope = {
    case global: GlobalSearchScope =>
      new ScalaFilterScope(global, project)
    case local: LocalSearchScope =>
      val filtered = local.getScope.filter(_.getLanguage.isKindOf(ScalaLanguage.INSTANCE))
      val displayName = local.getDisplayName + " in scala"
      new LocalSearchScope(filtered, displayName, local.isIgnoreInjectedPsi)
    case scope => scope
  }
}

class SourceFilterScope protected(elementScope: ElementScope) extends FilterScope(elementScope) {
  protected def isValid(file: VirtualFile): Boolean = myIndex.isInSourceContent(file)
}

object SourceFilterScope {
  def apply(project: Project): GlobalSearchScope =
    apply(project, projectScope(project))

  def apply(project: Project, scope: GlobalSearchScope): GlobalSearchScope = {
    val updatedScope = getScopeRestrictedByFileTypes(scope, ScalaFileType.INSTANCE, JavaFileType.INSTANCE)
    new SourceFilterScope(ElementScope(project, updatedScope))
  }
}
