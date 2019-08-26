package org.jetbrains.plugins.scala
package lang
package parser
package parsing

import org.jetbrains.plugins.scala.lang.parser.parsing.builder.ScalaPsiBuilder

trait ParsingRule {

  final def parse(implicit builder: ScalaPsiBuilder): Boolean = apply()

  def apply()(implicit builder: ScalaPsiBuilder): Boolean
}

object ParsingRule {

  object AlwaysTrue extends ParsingRule {
    override def apply()(implicit builder: ScalaPsiBuilder): Boolean = true
  }
}