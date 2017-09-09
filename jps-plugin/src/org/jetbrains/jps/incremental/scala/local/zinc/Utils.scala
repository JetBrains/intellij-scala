package org.jetbrains.jps.incremental.scala.local.zinc

import java.util.Optional

object Utils {
  implicit class EnrichOption[T](option: Option[T]) {
    def toOptional: Optional[T] = option match {
      case Some(value) => Optional.of(value)
      case None        => Optional.empty[T]
    }
  }

  implicit class EnrichOptional[T](optional: Optional[T]) {
    def toOption: Option[T] = if (!optional.isPresent) None else Some(optional.get())
  }
}
