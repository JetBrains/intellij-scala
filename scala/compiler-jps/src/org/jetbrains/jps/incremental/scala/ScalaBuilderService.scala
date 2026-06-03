package org.jetbrains.jps.incremental.scala

import org.jetbrains.annotations.NotNull
import org.jetbrains.jps.incremental._
import org.jetbrains.jps.incremental.resources.ResourcesBuilder

import _root_.java.{util => jutil}

class ScalaBuilderService extends BuilderService {
  ResourcesBuilder.registerEnabler(!ZincResourceBuilder.shouldSkip(_))

  @NotNull
  override def createModuleLevelBuilders: jutil.List[_ <: ModuleLevelBuilder] =
    jutil.Arrays.asList[ModuleLevelBuilder](
      new InitialScalaBuilder,
      new IdeaIncrementalBuilder(BuilderCategory.SOURCE_PROCESSOR),
      new IdeaIncrementalBuilder(BuilderCategory.OVERWRITING_TRANSLATOR),
      new SbtBuilder,
      new ScalaCompilerReferenceIndexBuilder,
      new ScalaClassPostProcessorBuilder()
    )

  override def createBuilders(): jutil.List[_ <: TargetBuilder[_, _]] =
    jutil.Collections.singletonList(new ZincResourceBuilder())
}
