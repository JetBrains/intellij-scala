package org.jetbrains.plugins.scala.findUsages.compilerReferences

import org.jetbrains.jps.backwardRefs.index.CompilerReferenceIndex
import org.jetbrains.plugins.scala.indices.protocol.CompilationInfo

sealed abstract class IndexerJob(val shouldRunUnderProgress: Boolean)

object IndexerJob {
  type IndexingHandler = Option[IndexerFailure] => Unit
  type Callback        = () => Unit

  final case class OpenWriter(isCleanBuild: Boolean)                                 extends IndexerJob(false)
  final case class ProcessCompilationInfo(data: CompilationInfo, onFinish: Callback) extends IndexerJob(true)
  final case class CloseWriter(onFinish: IndexingHandler)                            extends IndexerJob(false)
  final case class InvalidateIndex(index: Option[CompilerReferenceIndex[_]])         extends IndexerJob(false)
}
