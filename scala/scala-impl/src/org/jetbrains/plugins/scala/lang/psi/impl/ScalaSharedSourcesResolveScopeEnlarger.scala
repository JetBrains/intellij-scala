package org.jetbrains.plugins.scala.lang.psi.impl

import com.intellij.openapi.module.ModuleUtilCore
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.ResolveScopeEnlarger
import com.intellij.psi.search.{GlobalSearchScope, SearchScope}
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.plugins.scala.project.ModuleExt
import org.jetbrains.plugins.scala.settings.ScalaProjectSettings

@ApiStatus.Internal
final class ScalaSharedSourcesResolveScopeEnlarger extends ResolveScopeEnlarger {

  override def getAdditionalResolveScope(file: VirtualFile, project: Project): SearchScope = {
    val module = ModuleUtilCore.findModuleForFile(file, project)
    if (module == null)
      return null

    if (!ScalaProjectSettings.getInstance(module.getProject).isEnableBackReferencesFromSharedSources)
      return null

    val representativeModule = module.findRepresentativeModuleForSharedSourceModule.orNull
    if (representativeModule == null)
      return null

    GlobalSearchScope.moduleWithDependenciesScope(representativeModule)
  }
}
