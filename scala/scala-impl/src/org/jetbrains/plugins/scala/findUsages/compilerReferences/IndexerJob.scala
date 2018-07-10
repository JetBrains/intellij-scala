package org.jetbrains.plugins.scala.findUsages.compilerReferences

import org.jetbrains.plugin.scala.compilerReferences.ChunkBuildData

sealed trait IndexerJob

object IndexerJob {
  type IndexingHandler = Option[IndexerFailure] => Unit
  type Callback        = () => Unit

  final case class OpenWriter(isCleanBuild: Boolean, onFinish: Callback)      extends IndexerJob
  final case class ProcessChunkData(data: ChunkBuildData, onFinish: Callback) extends IndexerJob
  final case class CloseWriter(onFinish: IndexingHandler)                     extends IndexerJob
}
