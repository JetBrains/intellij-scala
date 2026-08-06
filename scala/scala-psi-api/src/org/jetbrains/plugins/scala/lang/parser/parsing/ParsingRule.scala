package org.jetbrains.plugins.scala.lang.parser.parsing

import org.jetbrains.plugins.scala.lang.parser.parsing.builder.ScalaPsiBuilder

trait ParsingRule {
  def parse(using builder: ScalaPsiBuilder): Boolean

  @inline
  final def apply()(using builder: ScalaPsiBuilder): Boolean = parse(using builder)
}

object ParsingRule {

  object AlwaysTrue extends ParsingRule {
    override def parse(using builder: ScalaPsiBuilder): Boolean = true
  }
}
