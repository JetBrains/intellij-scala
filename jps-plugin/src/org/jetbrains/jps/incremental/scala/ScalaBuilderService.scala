package org.jetbrains.jps.incremental.scala

import java.util

import org.jetbrains.annotations.NotNull
import org.jetbrains.jps.incremental._
import org.jetbrains.jps.incremental.resources.{StandardResourceBuilderEnabler, ResourcesBuilder}
import org.jetbrains.jps.incremental.scala.sources.{SbtModuleType, SharedSourcesModuleType}
import org.jetbrains.jps.model.module.JpsModule

/**
 * Nikolay.Tropin
 * 11/19/13
 */
class ScalaBuilderService extends BuilderService {
  ResourcesBuilder.registerEnabler(new StandardResourceBuilderEnabler {
    def isResourceProcessingEnabled(module: JpsModule): Boolean = {
      val moduleType = module.getModuleType
      moduleType != SbtModuleType.INSTANCE && moduleType != SharedSourcesModuleType.INSTANCE
    }
  })

  @NotNull
  override def createModuleLevelBuilders: util.List[_ <: ModuleLevelBuilder] = {
    util.Arrays.asList[ModuleLevelBuilder](
      new IdeaIncrementalBuilder(BuilderCategory.SOURCE_PROCESSOR),
      new IdeaIncrementalBuilder(BuilderCategory.OVERWRITING_TRANSLATOR),
      new SbtBuilder
    )
  }
}
