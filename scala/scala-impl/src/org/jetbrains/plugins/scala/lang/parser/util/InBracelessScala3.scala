package org.jetbrains.plugins.scala.lang.parser.util

import org.jetbrains.plugins.scala.lang.parser.parsing.builder.ScalaPsiBuilder

object InBracelessScala3 {
  def unapply[T](element: T)(implicit builder: ScalaPsiBuilder): Option[T] =
    Option.when(builder.isScala3 && builder.isScala3IndentationBasedSyntaxEnabled)(element)
}
