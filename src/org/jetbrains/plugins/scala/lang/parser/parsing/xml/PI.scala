package org.jetbrains.plugins.scala
package lang
package parser
package parsing
package xml

import org.jetbrains.plugins.scala.lang.lexer.ScalaXmlTokenTypes
import org.jetbrains.plugins.scala.lang.parser.parsing.builder.ScalaPsiBuilder

/**
* @author Alexander Podkhalyuzin
* Date: 18.04.2008
*/

/*
 * PI ::= <? name [S charTag] ?>
 */

object PI {
  def parse(builder: ScalaPsiBuilder): Boolean = {
    val PIMarker = builder.mark
    builder.getTokenType match {
      case ScalaXmlTokenTypes.XML_PI_START => builder.advanceLexer()
      case _ =>
        PIMarker.drop
        return false
    }
    builder.getTokenType match {
      case ScalaXmlTokenTypes.XML_NAME => builder.advanceLexer()
      case _ => builder error ErrMsg("xml.name.expected")
    }
    while (Attribute parse builder) {}
    builder.getTokenType match {
      case ScalaXmlTokenTypes.XML_TAG_CHARACTERS => builder.advanceLexer()
      case _ =>
    }
    builder.getTokenType match {
      case ScalaXmlTokenTypes.XML_PI_END => builder.advanceLexer()
      case _ => builder error ErrMsg("xml.PI.end.expected")
    }
    PIMarker.done(ScalaElementTypes.XML_PI)
    true
  }
}