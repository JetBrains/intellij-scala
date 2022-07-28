package org.jetbrains.plugins.scala
package lang
package parser
package parsing
package xml.pattern

import org.jetbrains.plugins.scala.lang.parser.parsing.builder.ScalaPsiBuilder

/*
 * XmlPattern ::= EmptyElemTagP
 *              | STagP ContentP ETagP
 */

object XmlPattern extends ParsingRule {
  override def parse(implicit builder: ScalaPsiBuilder): Boolean = {
    val patternMarker = builder.mark()
    builder.disableNewlines()
    if (EmptyElemTagP()) {
      patternMarker.done(ScalaElementType.XML_PATTERN)
      builder.restoreNewlinesState()
      return true
    }
    if (!STagP()) {
      patternMarker.drop()
      builder.restoreNewlinesState()
      return false
    }
    ContentP()
    if (!ETagP()) {
      builder error ErrMsg("xml.end.tag.expected")
    }
    builder.restoreNewlinesState()
    patternMarker.done(ScalaElementType.XML_PATTERN)
    true
  }
}