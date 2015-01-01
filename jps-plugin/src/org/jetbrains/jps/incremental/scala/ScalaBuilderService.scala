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
    util.Arrays.asList[ModuleLevelBuilder](
      new IdeaIncrementalBuilder(BuilderCategory.SOURCE_PROCESSOR),
      new IdeaIncrementalBuilder(BuilderCategory.OVERWRITING_TRANSLATOR),
      new SbtBuilder
    )
  }
}
