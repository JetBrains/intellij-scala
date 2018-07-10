package org.jetbrains.plugins.scala.findUsages.compilerReferences

import java.io.File
import scala.collection.Set

sealed trait IndexerFailure

object IndexerFailure {
  final case class FailedToParse(classes: Set[File])      extends IndexerFailure
  final case class FatalFailure(causes:   Set[Throwable]) extends IndexerFailure
}
