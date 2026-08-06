package org.jetbrains.plugins.scala.packagesearch

import com.intellij.buildsystem.model.unified.UnifiedDependency
import com.jetbrains.packagesearch.intellij.plugin.extensibility.{BuildSystemType, PackageSearchModule, ResolvedDependenciesProvider}

import java.util

class SbtResolvedDependenciesProvider extends ResolvedDependenciesProvider {
  override def getSupportedBuildSystems: util.Set[BuildSystemType] =
    java.util.Collections.singleton(SbtModuleTransformer.buildSystemType)

  override def resolvedDependencies(packageSearchModule: PackageSearchModule): util.List[UnifiedDependency] =
    util.Collections.emptyList() // TODO: implement (also see SCL-19838)
}
