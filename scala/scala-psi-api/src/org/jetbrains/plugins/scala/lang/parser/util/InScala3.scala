package org.jetbrains.plugins.scala.lang.parser.util

import org.jetbrains.plugins.scala.lang.parser.parsing.builder.ScalaPsiBuilder

object InScala3 {
  def unapply[T](element: T)(using builder: ScalaPsiBuilder): Option[T] =
    Option.when(builder.isScala3)(element)
}
