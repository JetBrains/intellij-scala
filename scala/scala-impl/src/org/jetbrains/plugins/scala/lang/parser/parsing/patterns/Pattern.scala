package org.jetbrains.plugins.scala
package lang
package parser
package parsing
package patterns

import org.jetbrains.plugins.scala.lang.parser.parsing.builder.ScalaPsiBuilder

/*
 * Patern ::= Pattern1 {'|' Pattern1}
 */
object Pattern extends ParsingRule {
  override def parse(implicit builder: ScalaPsiBuilder): Boolean = {
    val patternMarker = builder.mark()
    if (!Pattern1()) {
      patternMarker.drop()
      return false
    }
    var isComposite = false
    while (builder.getTokenText == "|") {
      isComposite = true
      builder.advanceLexer() //Ate |
      if (!Pattern1()) {
        builder error ScalaBundle.message("wrong.pattern")
      }
    }
    if (isComposite) patternMarker.done(ScalaElementType.PATTERN)
    else patternMarker.drop()
    true
  }
}