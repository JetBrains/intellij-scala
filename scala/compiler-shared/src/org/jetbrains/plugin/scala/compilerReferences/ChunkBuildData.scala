package org.jetbrains.plugin.scala.compilerReferences

import org.jetbrains.jps.incremental.CompiledClass

final case class ChunkBuildData(
  compiledClasses: Set[CompiledClass],
  removedSources:  Set[String],
  affectedModules: Set[String]
)
