package org.jetbrains.plugins.scala.lang.parser.util

import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.parser.parsing.builder.ScalaPsiBuilder

object InScala3 {
  def unapply[T](element: T)(implicit builder: ScalaPsiBuilder): Option[T] =
    builder.isScala3.option(element)

  object orSource3 {
    def unapply[T](element: T)(implicit builder: ScalaPsiBuilder): Option[T] =
      builder.isScala3orSource3.option(element)
  }
}
