package org.jetbrains.jps.incremental.scala

import java.util

import org.jetbrains.annotations.NotNull
import org.jetbrains.jps.incremental._

/**
 * Nikolay.Tropin
 * 11/19/13
 */
class ScalaBuilderService extends BuilderService {
  @NotNull
  override def createModuleLevelBuilders: util.List[_ <: ModuleLevelBuilder] = {
    util.Arrays.asList[ScalaBuilder](
      new ScalaBuilder(BuilderCategory.SOURCE_PROCESSOR, IdeaIncrementalBuilder),
      new ScalaBuilder(BuilderCategory.OVERWRITING_TRANSLATOR, IdeaIncrementalBuilder),
      new ScalaBuilder(BuilderCategory.TRANSLATOR, SbtBuilder)
    )
  }
}
