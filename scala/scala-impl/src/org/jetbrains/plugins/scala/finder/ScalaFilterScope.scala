package org.jetbrains.plugins.scala.finder

import com.intellij.ide.highlighter.{JavaClassFileType, JavaFileType}
import com.intellij.ide.scratch.ScratchUtil
import com.intellij.openapi.fileTypes.{FileType, FileTypeRegistry, LanguageFileType}
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.search.searches.{MethodReferencesSearch, ReferencesSearch}
import com.intellij.psi.search.{GlobalSearchScope, LocalSearchScope, SearchScope}
import org.jetbrains.plugins.scala.{ScalaFileType, ScalaLanguage}
import org.jetbrains.plugins.scala.lang.psi.compiled.SigFileType
import org.jetbrains.plugins.scala.tasty.TastyFileType

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
    apply(parameters.getEffectiveSearchScope)(using parameters.getProject)

  def apply(parameters: MethodReferencesSearch.SearchParameters): SearchScope =
    apply(parameters.getEffectiveSearchScope)(using parameters.getProject)

  def apply(delegate: GlobalSearchScope)
           (implicit project: Project): ScalaFilterScope = new ScalaFilterScope(delegate)(using project)

  def apply(scope: SearchScope)
           (implicit project: Project): SearchScope = scope match {
    case global: GlobalSearchScope => apply(global)
    case local: LocalSearchScope => new LocalSearchScope(
      local.getScope.filter { e =>
        e.getLanguage.isKindOf(ScalaLanguage.INSTANCE) ||
          (e.getContainingFile.getFileType match {
            case lft: LanguageFileType => ScalaLanguageDerivative.existsFor(lft)
            case _ => false
          })
      },
      local.getDisplayName + " in scala",
      local.isIgnoreInjectedPsi
    )
    case _ => scope
  }
}

final class SourceFilterScope private(delegate: GlobalSearchScope, fileTypes: Seq[FileType])
                                     (implicit project: Project)
  extends FilterScope(GlobalSearchScope.getScopeRestrictedByFileTypes(delegate, fileTypes*)) {

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
