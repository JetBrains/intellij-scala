package org.jetbrains.plugins.scala
package lang
package parser
package parsing
package xml

import org.jetbrains.plugins.scala.lang.lexer.ScalaXmlTokenTypes
import org.jetbrains.plugins.scala.lang.parser.parsing.builder.ScalaPsiBuilder

import scala.annotation.tailrec

/*
 *  Content ::= [CharData] {Content1 [CharData]}
 *
 *  Content1 ::= XmlContent
 *             | Reference
 *             | ScalaExpr
 */

object Content extends ParsingRule {
  override def parse(implicit builder: ScalaPsiBuilder): Boolean = {
    val contentMarker = builder.mark()
    builder.getTokenType match {
      case ScalaXmlTokenTypes.XML_DATA_CHARACTERS =>
        builder.advanceLexer()
      case ScalaXmlTokenTypes.XML_CHAR_ENTITY_REF =>
        builder.advanceLexer()
      case _ =>
    }

    @tailrec
    def subparse(): Unit = {
      val isReturn =
        if (XmlContent() ||
          ScalaExpr() ||
          builder.skipExternalToken())
          false
        else
          true

      builder.getTokenType match {
        case ScalaXmlTokenTypes.XML_DATA_CHARACTERS |
             ScalaXmlTokenTypes.XML_CHAR_ENTITY_REF |
             ScalaXmlTokenTypes.XML_ENTITY_REF_TOKEN =>
          builder.advanceLexer()
          subparse()
        case _ if isReturn =>
        case _ => subparse()
      }
    }

    subparse()
    contentMarker.drop()
    true
  }
}