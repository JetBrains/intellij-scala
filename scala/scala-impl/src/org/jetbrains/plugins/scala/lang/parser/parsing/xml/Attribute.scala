package org.jetbrains.plugins.scala
package lang
package parser
package parsing
package xml

import org.jetbrains.plugins.scala.lang.lexer.ScalaXmlTokenTypes
import org.jetbrains.plugins.scala.lang.parser.parsing.builder.ScalaPsiBuilder

/*
 * Attribute ::= S Name Eq AttValue
 */

object Attribute extends ParsingRule {
  override def parse(implicit builder: ScalaPsiBuilder): Boolean = {
    val attributeMarker = builder.mark()
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
        attributeMarker.done(ScalaElementType.XML_ATTRIBUTE)
        return true
    }
    if (!AttrValue()) builder error ErrMsg("xml.attribute.value.expected")
    attributeMarker.done(ScalaElementType.XML_ATTRIBUTE)
    true
  }
}