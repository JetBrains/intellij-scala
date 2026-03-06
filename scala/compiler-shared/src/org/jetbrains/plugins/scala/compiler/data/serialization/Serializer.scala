package org.jetbrains.plugins.scala.compiler.data.serialization

import org.jetbrains.annotations.ApiStatus
import org.jetbrains.jps.incremental.scala.remote.{NioPathTranslator, PathTranslator}

trait Serializer {
  type From
  type To
  type ErrorsHolder[_]

  @deprecated(message = "Use serialize(From, PathTranslator). Kept for preserving binary compatibility.", since = "2026.1")
  @Deprecated
  @ApiStatus.ScheduledForRemoval(inVersion = "2026.2")
  def serialize(value: From): To = serialize(value, NioPathTranslator)
  def serialize(value: From, translator: PathTranslator): To =
    throw AbstractMethodError("This method must be overridden. The exception is thrown to preserve binary compatibility.")
  def deserialize(value: To): ErrorsHolder[From]
}
