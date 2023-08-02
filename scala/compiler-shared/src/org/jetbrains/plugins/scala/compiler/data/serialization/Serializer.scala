package org.jetbrains.plugins.scala.compiler.data.serialization

trait Serializer {
  type From
  type To
  type ErrorsHolder[_]

  def serialize(value: From): To
  def deserialize(value: To): ErrorsHolder[From]
}
