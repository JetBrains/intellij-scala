package org.jetbrains.plugin.scala.compilerReferences

import org.jetbrains.jps.incremental.CompiledClass

final case class BuildData(
  timeStamp: Long,
  compiledClasses: Set[CompiledClass],
  removedSources: Set[String],
  affectedModules: Set[String],
  isRebuild: Boolean
)
