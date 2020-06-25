package org.jetbrains.plugins.scala.compiler.data.serialization

import scala.language.higherKinds

trait Serializer {
  type From
  type To
  type ErrorsHolder[_]

  def serialize(value: From): To
  def deserialize(value: To): ErrorsHolder[From]
}
