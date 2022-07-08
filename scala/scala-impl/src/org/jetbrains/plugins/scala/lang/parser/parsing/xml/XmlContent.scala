package org.jetbrains.plugins.scala
package lang
package parser
package parsing
package xml

import org.jetbrains.plugins.scala.lang.lexer.ScalaXmlTokenTypes
import org.jetbrains.plugins.scala.lang.parser.parsing.builder.ScalaPsiBuilder

/*
 * XmlContent ::= Element
 *              | CDSect
 *              | PI
 *              | Comment
 */

object XmlContent extends ParsingRule {
  override def parse(implicit builder: ScalaPsiBuilder): Boolean = {
    builder.getTokenType match {
      case ScalaXmlTokenTypes.XML_START_TAG_START =>
        Element()
      case ScalaXmlTokenTypes.XML_COMMENT_START =>
        Comment()
      case ScalaXmlTokenTypes.XML_CDATA_START =>
        CDSect()
      case ScalaXmlTokenTypes.XML_PI_START =>
        PI()
      case _ => false
    }
  }
}

