package org.jetbrains.plugins.scala
package finder

import com.intellij.ide.highlighter.{JavaClassFileType, JavaFileType}
import com.intellij.openapi.fileTypes.{FileType, FileTypeRegistry, LanguageFileType}
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.search.searches.{MethodReferencesSearch, ReferencesSearch}
import com.intellij.psi.search.{GlobalSearchScope, LocalSearchScope, SearchScope}
import org.jetbrains.plugins.scala.lang.psi.ElementScope

/**
  * User: Alexander Podkhalyuzin
  * Date: 17.02.2010
  */
sealed abstract class FilterScope protected(elementScope: ElementScope) extends GlobalSearchScope(elementScope.project) {
  def this(scope: GlobalSearchScope, project: Project) =
    this(ElementScope(project, scope))

  private val myDelegate = elementScope.scope

  private val myIndex = ProjectRootManager.getInstance(elementScope.project).getFileIndex

  protected final def isInSourceContent(file: VirtualFile): Boolean =
    myIndex.isInSourceContent(file)

  protected final def isInLibraryClasses(file: VirtualFile): Boolean =
    myIndex.isInLibraryClasses(file)

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

final class ScalaFilterScope(scope: GlobalSearchScope, project: Project) extends FilterScope(scope, project) {

  import ScalaFilterScope._

  override protected def isValid(file: VirtualFile): Boolean =
    isInSourceContent(file) && hasScalaPsi(file) ||
      isClassFile(file) && isInLibraryClasses(file)
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

  private def hasScalaPsi(file: VirtualFile) =
    FileTypeRegistry.getInstance.getFileTypeByFile(file) match {
      case fileType: LanguageFileType if fileType.getLanguage.isKindOf(ScalaLanguage.INSTANCE) => true
      case fileType => ScalaLanguageDerivative.existsFor(fileType)
    }

  /** performance critical
   * finding [[JavaClassFileType]] by [[VirtualFile]] should be avoided
   */
  private def isClassFile(file: VirtualFile) =
    JavaClassFileType.INSTANCE.getDefaultExtension == file.getExtension
}

final class SourceFilterScope protected(elementScope: ElementScope) extends FilterScope(elementScope) {

  override protected def isValid(file: VirtualFile): Boolean = isInSourceContent(file)
}

object SourceFilterScope {
  def apply(project: Project): GlobalSearchScope =
    apply(project, GlobalSearchScope.projectScope(project))

  def apply(project: Project, scope: GlobalSearchScope): GlobalSearchScope = {
    apply(project, scope, Seq(ScalaFileType.INSTANCE, JavaFileType.INSTANCE))
  }

  def apply(project: Project, scope: GlobalSearchScope, fileTypes: Seq[FileType]): GlobalSearchScope = {
    val updatedScope = GlobalSearchScope.getScopeRestrictedByFileTypes(scope, fileTypes: _*)
    new SourceFilterScope(ElementScope(project, updatedScope))
  }
}
