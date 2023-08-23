package org.jetbrains.plugins.scala.lang.psi.impl.search

import com.intellij.openapi.module
import com.intellij.openapi.module.ModuleUtilCore
import com.intellij.psi.search.{GlobalSearchScope, LocalSearchScope, SearchScope, UseScopeEnlarger}
import com.intellij.psi.{PsiElement, PsiMember}
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.plugins.scala.caches.cachedInUserData
import org.jetbrains.plugins.scala.lang.psi.api.ScalaPsiManager
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

    val sharedModules: Array[module.Module] = cachedInUserData("getAdditionalUseScope.sharedModules", psiFile, ScalaPsiManager.instance(psiFile.getProject).TopLevelModificationTracker, Tuple1(psiFile)) {
      val module = ModuleUtilCore.findModuleForPsiElement(psiFile)
      if (module == null) Array.empty else module.sharedSourceDependencies.toArray
    }

    if (sharedModules.isEmpty)
      return null

    val useScope = psiMember.getUseScope
    //If member has local search scope we know it can't be used in shared sources
    if (useScope.isInstanceOf[LocalSearchScope])
      return null

    val sharedModuleScopes = sharedModules.map(_.getModuleScope(true))
    GlobalSearchScope.union(sharedModuleScopes)
  }
}
