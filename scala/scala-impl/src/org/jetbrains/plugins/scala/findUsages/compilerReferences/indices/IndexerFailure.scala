package org.jetbrains.plugins.scala.findUsages.compilerReferences.indices

import org.jetbrains.plugins.scala.findUsages.compilerReferences.indices.CompilerReferenceIndexer.IndexerJobFailure

sealed trait IndexerFailure

object IndexerFailure {
  final case class FailedToParse(failures: Iterable[IndexerJobFailure]) extends IndexerFailure
  final case class FatalFailure(causes:    Throwable)                   extends IndexerFailure
}
