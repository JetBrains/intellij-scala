package org.jetbrains.plugins.scala
package finder

import com.intellij.ide.highlighter.{JavaClassFileType, JavaFileType}
import com.intellij.ide.scratch.ScratchUtil
import com.intellij.openapi.fileTypes.{FileType, FileTypeRegistry, LanguageFileType}
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.search.searches.{MethodReferencesSearch, ReferencesSearch}
import com.intellij.psi.search.{GlobalSearchScope, LocalSearchScope, SearchScope}
import org.jetbrains.jps.model.java.JavaModuleSourceRootTypes
import org.jetbrains.plugins.scala.extensions.ObjectExt
import org.jetbrains.plugins.scala.lang.psi.compiled.SigFileType
import org.jetbrains.plugins.scala.tasty.TastyFileType
import org.jetbrains.plugins.scala.util.HashBuilder._

sealed abstract class FilterScope(val delegate: GlobalSearchScope)
                                 (implicit project: Project)
  extends GlobalSearchScope(project) {

  private val fileIndex =
    ProjectRootManager.getInstance(project).getFileIndex

  protected final def isInSourceContent(file: VirtualFile): Boolean =
    fileIndex.isUnderSourceRootOfType(file, JavaModuleSourceRootTypes.SOURCES)

  protected final def isInLibraryClasses(file: VirtualFile): Boolean =
    fileIndex.isInLibraryClasses(file)

  override final def contains(file: VirtualFile): Boolean =
    (null == delegate || delegate.contains(file)) && mayContain(file)

  protected def mayContain(file: VirtualFile): Boolean

  override def compare(file1: VirtualFile, file2: VirtualFile): Int =
    if (delegate != null) delegate.compare(file1, file2) else 0

  override def isSearchInModuleContent(aModule: Module): Boolean =
    delegate == null || delegate.isSearchInModuleContent(aModule)

  override def isSearchInLibraries: Boolean =
    delegate == null || delegate.isSearchInLibraries

  override def calcHashCode(): Int =
    this.getClass.hashCode() #+ delegate.hashCode()

  override def equals(other: Any): Boolean = other match {
    case that: FilterScope if this.getClass == that.getClass =>
      delegate == that.delegate
    case _ => false
  }
}

final class ScalaFilterScope private(delegate: GlobalSearchScope)
                                    (implicit project: Project)
  extends FilterScope(delegate) {

  override protected def mayContain(file: VirtualFile): Boolean =
    FileTypeRegistry.getInstance.getFileTypeByFile(file) match {
      case _: JavaClassFileType | SigFileType | TastyFileType =>
        isInLibraryClasses(file)
      case fileType: LanguageFileType =>
        val hasScala = fileType.getLanguage.isKindOf(ScalaLanguage.INSTANCE) || ScalaLanguageDerivative.existsFor(fileType)
        if (hasScala)
          isInSourceContent(file) || ScratchUtil.isScratch(file)
        else
          false
      case _ =>
        false
    }
}

object ScalaFilterScope {

  def apply(parameters: ReferencesSearch.SearchParameters): SearchScope =
    apply(parameters.getEffectiveSearchScope)(parameters.getProject)

  def apply(parameters: MethodReferencesSearch.SearchParameters): SearchScope =
    apply(parameters.getEffectiveSearchScope)(parameters.getProject)

  def apply(delegate: GlobalSearchScope)
           (implicit project: Project): ScalaFilterScope = new ScalaFilterScope(delegate)(project)

  def apply(scope: SearchScope)
           (implicit project: Project): SearchScope = scope match {
    case global: GlobalSearchScope => apply(global)
    case local: LocalSearchScope => new LocalSearchScope(
      local.getScope.filter(_.getLanguage.isKindOf(ScalaLanguage.INSTANCE)),
      local.getDisplayName + " in scala",
      local.isIgnoreInjectedPsi
    )
    case _ => scope
  }
}

final class SourceFilterScope private(delegate: GlobalSearchScope, fileTypes: Seq[FileType])
                                     (implicit project: Project)
  extends FilterScope(GlobalSearchScope.getScopeRestrictedByFileTypes(delegate, fileTypes: _*)) {

  override protected def mayContain(file: VirtualFile): Boolean = isInSourceContent(file)
}

object SourceFilterScope {

  import GlobalSearchScope.projectScope

  def apply(delegate: GlobalSearchScope, fileTypes: Seq[FileType])
           (implicit project: Project): SourceFilterScope =
    new SourceFilterScope(delegate, fileTypes)

  def apply(fileTypes: Seq[FileType])
           (implicit project: Project): SourceFilterScope =
    new SourceFilterScope(projectScope(project), fileTypes)

  def apply(scope: GlobalSearchScope)
           (implicit project: Project): SourceFilterScope =
    new SourceFilterScope(scope, Seq(ScalaFileType.INSTANCE, JavaFileType.INSTANCE))
}

abstract class ResolveFilterScopeBase(delegate: GlobalSearchScope)
                                     (implicit project: Project)
  extends FilterScope(delegate) {

  override protected def mayContain(file: VirtualFile): Boolean =
    isInLibraryClasses(file) || isInSourceContent(file) || ScratchUtil.isScratch(file)
}

final class ResolveFilterScope(delegate: GlobalSearchScope)
                              (implicit project: Project)
  extends ResolveFilterScopeBase(delegate) {

  override def mayContain(file: VirtualFile): Boolean =
    isInLibraryClasses(file) ||
      ((isInSourceContent(file) || ScratchUtil.isScratch(file)) && !file.getFileType.is[FileTypeWithIsolatedDeclarations])
}

object ResolveFilterScope {
  def apply(delegate: GlobalSearchScope)
           (implicit project: Project): ResolveFilterScope =
    new ResolveFilterScope(delegate)
}

final class WorksheetResolveFilterScope(delegate: GlobalSearchScope,
                                        val worksheetFile: VirtualFile)
                                       (implicit project: Project)
  extends ResolveFilterScopeBase(delegate){


  override def equals(other: Any): Boolean =
    super.equals(other) && (other match {
      case wsScope: WorksheetResolveFilterScope => this.worksheetFile == wsScope.worksheetFile
      case _                                    => false
    })

  override def calcHashCode(): Int =
    super.calcHashCode() #+ worksheetFile.hashCode()

  override def mayContain(file: VirtualFile): Boolean =
    super.mayContain(file) && {
      if (file.getFileType.is[FileTypeWithIsolatedDeclarations])
        file == worksheetFile // worksheet elements shouldn't be available outside the worksheet
      else
        true
    }
}

object WorksheetResolveFilterScope {
  def apply(delegate: GlobalSearchScope, worksheetFile: VirtualFile)
           (implicit project: Project): WorksheetResolveFilterScope =
    new WorksheetResolveFilterScope(delegate, worksheetFile)(project)
}