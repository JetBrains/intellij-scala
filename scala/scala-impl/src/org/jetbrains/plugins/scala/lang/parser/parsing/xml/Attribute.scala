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
 * Attribute ::= S Name Eq AttValue
 */

object Attribute {
  def parse(builder: ScalaPsiBuilder): Boolean = {
    val attributeMarker = builder.mark
    /*builder.getTokenType match {
      case ScalaXmlTokenTypes.XML_WHITE_SPACE => builder.advanceLexer()
      case _ => {
        attributeMarker.drop()
        return false
      }
    }*/
    builder.getTokenType match {
      case ScalaXmlTokenTypes.XML_NAME => builder.advanceLexer()
      case _ => 
        attributeMarker.rollbackTo()
        return false
    }
    builder.getTokenType match {
      case ScalaXmlTokenTypes.XML_EQ => builder.advanceLexer()
      case _ => 
        builder error ErrMsg("xml.eq.expected")
        attributeMarker.done(ScalaElementTypes.XML_ATTRIBUTE)
        return true
    }
    if (!AttrValue.parse(builder)) builder error ErrMsg("xml.attribute.value.expected")
    attributeMarker.done(ScalaElementTypes.XML_ATTRIBUTE)
    true
  }
}