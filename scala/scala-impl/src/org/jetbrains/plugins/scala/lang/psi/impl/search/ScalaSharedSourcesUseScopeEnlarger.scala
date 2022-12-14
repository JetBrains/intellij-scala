package org.jetbrains.plugins.scala.lang.psi.impl.search

import com.intellij.openapi.module.ModuleUtilCore
import com.intellij.psi.search.{GlobalSearchScope, LocalSearchScope, SearchScope, UseScopeEnlarger}
import com.intellij.psi.{PsiElement, PsiFile, PsiMember}
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.plugins.scala.caches.cachedInUserData
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiManager
import org.jetbrains.plugins.scala.project.ModuleExt
import org.jetbrains.plugins.scala.settings.ScalaProjectSettings

@ApiStatus.Internal
class ScalaSharedSourcesUseScopeEnlarger extends UseScopeEnlarger {
  override def getAdditionalUseScope(element: PsiElement): SearchScope = {
    val psiMember = element match {
      case m: PsiMember => m
      case _ =>
        return null
    }

    val psiFile = psiMember.getContainingFile
    if (psiFile == null)
      return null

    if (!ScalaProjectSettings.getInstance(psiFile.getProject).isEnableBackReferencesFromSharedSources)
      return null

    val sharedModules = findSharedSourceModuleDependencies(psiFile)
    if (sharedModules.isEmpty)
      return null

    val useScope = psiMember.getUseScope
    //If member has local search scope we know it can't be used in shared sources
    if (useScope.isInstanceOf[LocalSearchScope])
      return null

    val sharedModuleScopes = sharedModules.map(_.getModuleScope(true))
    GlobalSearchScope.union(sharedModuleScopes)
  }

  private val findSharedSourceModuleDependencies = (holder: PsiFile) => cachedInUserData("ScalaSharedSourcesUseScopeEnlarger.findSharedSourceModuleDependencies", holder, ScalaPsiManager.instance(holder.getProject).TopLevelModificationTracker, (file: PsiFile) => {
    val module = ModuleUtilCore.findModuleForPsiElement(file)
    if (module == null)
      Array.empty
    else
      module.sharedSourceDependencies.toArray
  }).apply(holder)
}
