package org.jetbrains.plugins.scala.findUsages.compilerReferences.indices

import org.jetbrains.plugins.scala.findUsages.compilerReferences.indices.CompilerReferenceIndexer.ClassParsingFailure

sealed trait IndexerFailure

object IndexerFailure {
  final case class FailedToParse(failures: Iterable[ClassParsingFailure]) extends IndexerFailure
  final case class FatalFailure(causes:    Throwable)                     extends IndexerFailure
}
