package org.jetbrains.plugins.scala.compiler.references.indices

import org.jetbrains.plugins.scala.compiler.references.indices.CompilerReferenceIndexer.IndexerJobFailure

sealed trait IndexerFailure

object IndexerFailure {
  final case class FailedToParse(failures: Iterable[IndexerJobFailure]) extends IndexerFailure
  final case class FatalFailure(causes:    Throwable)                   extends IndexerFailure
}
