package org.jetbrains.plugins.scala
package lang
package parser
package parsing

import org.jetbrains.plugins.scala.lang.parser.parsing.builder.ScalaPsiBuilder

trait ParsingRule {
  def parse(implicit builder: ScalaPsiBuilder): Boolean

  @inline
  final def apply()(implicit builder: ScalaPsiBuilder): Boolean = parse(builder)
}

object ParsingRule {

  object AlwaysTrue extends ParsingRule {
    override def parse(implicit builder: ScalaPsiBuilder): Boolean = true
  }
}