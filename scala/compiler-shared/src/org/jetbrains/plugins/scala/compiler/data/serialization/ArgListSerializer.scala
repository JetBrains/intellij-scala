package org.jetbrains.plugins.scala.compiler.data.serialization

import org.jetbrains.plugins.scala.compiler.data.serialization.ArgListSerializer._

trait ArgListSerializer[A] extends Serializer {

  type From = A
  type To = ArgList
  type ErrorsHolder[_] = Either[DeserializationError, _]

  protected val Delimiter = "\n"

  protected def error(message: String): Left[DeserializationError, A] = Left(Seq(message))
}

object ArgListSerializer {
  type ArgList = Seq[String]
  type DeserializationError = Seq[String]
}
