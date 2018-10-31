package org.jetbrains.plugins.scala.findUsages.compilerReferences

import org.jetbrains.plugins.scala.findUsages.compilerReferences.CompilerReferenceIndexer.ClassParsingFailure

sealed trait IndexerFailure

object IndexerFailure {
  final case class FailedToParse(failures: Iterable[ClassParsingFailure]) extends IndexerFailure
  final case class FatalFailure(causes:    Throwable)                     extends IndexerFailure
}
