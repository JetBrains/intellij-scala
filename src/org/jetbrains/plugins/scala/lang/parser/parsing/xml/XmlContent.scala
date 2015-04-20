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
 * XmlContent ::= Element
 *              | CDSect
 *              | PI
 *              | Comment
 */

object XmlContent {
  def parse(builder: ScalaPsiBuilder): Boolean = {
    builder.getTokenType match {
      case ScalaXmlTokenTypes.XML_START_TAG_START =>
        Element parse builder
      case ScalaXmlTokenTypes.XML_COMMENT_START =>
        Comment parse builder
      case ScalaXmlTokenTypes.XML_CDATA_START =>
        CDSect parse builder
      case ScalaXmlTokenTypes.XML_PI_START =>
        PI parse builder
      case _ => false
    }
  }
}

