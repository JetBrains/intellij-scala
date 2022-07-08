package org.jetbrains.jps.incremental.scala

import _root_.java.{util => jutil}

import org.jetbrains.annotations.NotNull
import org.jetbrains.jps.incremental._
import org.jetbrains.jps.incremental.resources.ResourcesBuilder
import org.jetbrains.jps.incremental.scala.sources.{SbtModuleType, SharedSourcesModuleType}

class ScalaBuilderService extends BuilderService {
  ResourcesBuilder.registerEnabler(module => {
    val moduleType = module.getModuleType
    moduleType != SbtModuleType.INSTANCE && moduleType != SharedSourcesModuleType.INSTANCE
  })

  @NotNull
  override def createModuleLevelBuilders: jutil.List[_ <: ModuleLevelBuilder] =
    jutil.Arrays.asList[ModuleLevelBuilder](
      new InitialScalaBuilder,
      new IdeaIncrementalBuilder(BuilderCategory.SOURCE_PROCESSOR),
      new IdeaIncrementalBuilder(BuilderCategory.OVERWRITING_TRANSLATOR),
      new SbtBuilder,
      new ScalaCompilerReferenceIndexBuilder
    )
}
