package org.jetbrains.plugins.scala
package lang
package parser
package parsing
package xml

import org.jetbrains.plugins.scala.lang.lexer.ScalaXmlTokenTypes
import org.jetbrains.plugins.scala.lang.parser.parsing.builder.ScalaPsiBuilder

/*
 * PI ::= <? name [S charTag] ?>
 */

object PI extends ParsingRule {
  override def parse(implicit builder: ScalaPsiBuilder): Boolean = {
    val PIMarker = builder.mark()
    builder.getTokenType match {
      case ScalaXmlTokenTypes.XML_PI_START => builder.advanceLexer()
      case _ =>
        PIMarker.drop()
        return false
    }
    builder.getTokenType match {
      case ScalaXmlTokenTypes.XML_NAME => builder.advanceLexer()
      case _ => builder error ErrMsg("xml.name.expected")
    }
    while (Attribute()) {}
    builder.getTokenType match {
      case ScalaXmlTokenTypes.XML_TAG_CHARACTERS => builder.advanceLexer()
      case _ =>
    }
    builder.getTokenType match {
      case ScalaXmlTokenTypes.XML_PI_END => builder.advanceLexer()
      case _ => builder error ErrMsg("xml.PI.end.expected")
    }
    PIMarker.done(ScalaElementType.XML_PI)
    true
  }
}