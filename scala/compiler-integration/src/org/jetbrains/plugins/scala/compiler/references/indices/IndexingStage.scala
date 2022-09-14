package org.jetbrains.plugins.scala.compiler.references.indices

import org.jetbrains.jps.backwardRefs.index.CompilerReferenceIndex
import org.jetbrains.plugins.scala.indices.protocol.CompilationInfo

sealed trait IndexingStage

object IndexingStage {
  type IndexingHandler = Option[IndexerFailure] => Unit
  type Callback        = () => Unit

  final case class OpenWriter(isCleanBuild: Boolean)                                 extends IndexingStage
  final case class ProcessCompilationInfo(data: CompilationInfo, onFinish: Callback) extends IndexingStage
  final case class CloseWriter(onFinish: IndexingHandler)                            extends IndexingStage
  final case class InvalidateIndex(index: Option[CompilerReferenceIndex[_]])         extends IndexingStage
}
