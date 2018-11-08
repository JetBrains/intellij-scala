package org.jetbrains.plugins.scala.findUsages.compilerReferences

import org.jetbrains.plugins.scala.indices.protocol.CompilationInfo

sealed trait IndexerJob

object IndexerJob {
  type IndexingHandler = Option[IndexerFailure] => Unit
  type Callback        = () => Unit

  final case class OpenWriter(isCleanBuild: Boolean)                                 extends IndexerJob
  final case class ProcessCompilationInfo(data: CompilationInfo, onFinish: Callback) extends IndexerJob
  final case class CloseWriter(onFinish: IndexingHandler)                            extends IndexerJob
  final case object InvalidateIndex                                                  extends IndexerJob
}
