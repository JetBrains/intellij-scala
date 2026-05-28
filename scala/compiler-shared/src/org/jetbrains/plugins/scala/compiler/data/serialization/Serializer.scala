package org.jetbrains.plugins.scala.compiler.data.serialization

import org.jetbrains.jps.incremental.scala.remote.PathTranslator

trait Serializer {
  type From
  type To
  type ErrorsHolder[_]

  def serialize(value: From, translator: PathTranslator): To
  def deserialize(value: To): ErrorsHolder[From]
}
