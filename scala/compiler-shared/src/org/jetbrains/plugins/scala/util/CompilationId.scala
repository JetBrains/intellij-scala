package org.jetbrains.plugins.scala.util

final case class CompilationId(timestamp: Long, documentVersion: Option[DocumentVersion]) extends Serializable
