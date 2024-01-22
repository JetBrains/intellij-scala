package org.jetbrains.sbt.language.psi.search

import com.intellij.openapi.module.{Module, ModuleUtilCore}
import com.intellij.openapi.roots.ModuleRootManager
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.search.{GlobalSearchScope, LocalSearchScope, SearchScope, UseScopeEnlarger}
import com.intellij.psi.{PsiElement, PsiMember}
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.plugins.scala.extensions.ObjectExt
import org.jetbrains.plugins.scala.project.ModuleExt
import org.jetbrains.sbt.language.SbtFileType

/**
 * This class is designed to extend use scope of definitions located in files in `project-root/project/` folder in sbt projects.
 * This folder is a content root of `-build` module.
 * Definitions from this folder can be used `project-root/_.sbt` files (e.g. from `build.sbt`)
 * However `project-root/_.sbt` files from  don't belong to the build module content root.
 * So we need to provide special logic in order find usages detects usages from `project` folder in `.sbt` files.
 *
 * @note dual logic is located in [[org.jetbrains.sbt.language.SbtFileImpl#getFileResolveScope]]
 * @todo investigate possibility to include `.sbt` files in `-build` module content/source roots via new WorkspaceModel
 */
@ApiStatus.Internal
class SbtBuildModuleUseScopeEnlarger extends UseScopeEnlarger {

  override def getAdditionalUseScope(element: PsiElement): SearchScope = {
    val psiMember = element match {
      case m: PsiMember => m
      case _ =>
        return null
    }

    val psiFile = psiMember.getContainingFile
    if (psiFile == null)
      return null

    val module = ModuleUtilCore.findModuleForPsiElement(psiFile)
    if (module == null)
      return null

    if (!module.isBuildModule)
      return null

    val useScope = psiMember.getUseScope
    //If member has local search scope we know it can't be used outside current file
    if (useScope.is[LocalSearchScope])
      return null

    val contentRoots = ModuleRootManager.getInstance(module).getContentRoots
    if (contentRoots.isEmpty)
      return null

    //NOTE: I am assuming there is the single content root in build module (I am not aware of other cases at the moment)
    val contentRootParent = contentRoots.head.getParent
    val project = module.getProject
    val moduleOfParentFolder = ModuleUtilCore.findModuleForFile(contentRootParent, project)
    if (moduleOfParentFolder == null)
      return null

    new GlobalSearchScope(project) {
      override def isSearchInLibraries: Boolean = false

      override def isSearchInModuleContent(aModule: Module): Boolean =
        aModule eq moduleOfParentFolder

      override def contains(file: VirtualFile): Boolean =
        file.getFileType == SbtFileType && (file.getParent eq contentRootParent)
    }
  }
}
