package org.jetbrains.plugins.scala.finder

import com.intellij.ide.scratch.ScratchUtil
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.search.GlobalSearchScope
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.plugins.scala.extensions.ObjectExt
import org.jetbrains.plugins.scala.project.ScalaFileLocation
import org.jetbrains.plugins.scala.util.HashBuilder.*

@ApiStatus.NonExtendable
abstract class FilterScope(private val delegate: GlobalSearchScope)(implicit project: Project)
  extends GlobalSearchScope(project) {

  private val location = ScalaFileLocation.instance

  protected final def isInSourceContent(file: VirtualFile): Boolean =
    location.isUnderSourceRoot(file, project)

  protected final def isInLibraryClasses(file: VirtualFile): Boolean =
    location.isInLibraryClasses(file, project)

  override final def contains(file: VirtualFile): Boolean =
    delegate.contains(file) && mayContain(file)

  protected def mayContain(file: VirtualFile): Boolean

  override def compare(file1: VirtualFile, file2: VirtualFile): Int =
    delegate.compare(file1, file2)

  override def isSearchInModuleContent(aModule: Module): Boolean =
    delegate.isSearchInModuleContent(aModule)

  override def isSearchInLibraries: Boolean =
    delegate.isSearchInLibraries

  override def calcHashCode(): Int =
    this.getClass.hashCode() #+ delegate.hashCode()

  override def equals(other: Any): Boolean = other match {
    case that: FilterScope if this.getClass == that.getClass =>
      delegate == that.delegate
    case _ => false
  }
}

@ApiStatus.NonExtendable
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
    new WorksheetResolveFilterScope(delegate, worksheetFile)(using project)
}
