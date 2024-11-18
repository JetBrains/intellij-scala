package org.jetbrains.plugins.scala.lang.parser.parsing

import org.jetbrains.plugins.scala.lang.parser.ScalaElementType
import org.jetbrains.plugins.scala.lang.parser.parsing.builder.ScalaPsiBuilder

object Blub extends ParsingRule {

  override def parse(implicit builder: ScalaPsiBuilder): Boolean = {
    val m = builder.mark()
    builder.advanceLexer()
    m.done(ScalaElementType.BLOCK)
    true
  }
}
