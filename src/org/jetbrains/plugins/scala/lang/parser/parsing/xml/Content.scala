package org.jetbrains.plugins.scala
package lang
package parser
package parsing
package xml

import org.jetbrains.plugins.scala.lang.lexer.ScalaXmlTokenTypes
import org.jetbrains.plugins.scala.lang.parser.parsing.builder.ScalaPsiBuilder
import org.jetbrains.plugins.scala.lang.parser.util.ParserPatcher

/**
* @author Alexander Podkhalyuzin
* Date: 18.04.2008
*/

/*
 *  Content ::= [CharData] {Content1 [CharData]}
 *
 *  Content1 ::= XmlContent
 *             | Reference
 *             | ScalaExpr
 */

object Content {
  def parse(builder: ScalaPsiBuilder): Boolean = {
    val contentMarker = builder.mark()
    builder.getTokenType match {
      case ScalaXmlTokenTypes.XML_DATA_CHARACTERS =>
        builder.advanceLexer()
      case ScalaXmlTokenTypes.XML_CHAR_ENTITY_REF =>
        builder.advanceLexer()
      case _ =>
    }
    
    val patcher = ParserPatcher.getSuitablePatcher(builder)
    
    def subparse() {
      var isReturn = false
      if (!XmlContent.parse(builder) &&
              !Reference.parse(builder) &&
              !ScalaExpr.parse(builder) && !patcher.parse(builder)) isReturn = true
      builder.getTokenType match {
        case ScalaXmlTokenTypes.XML_DATA_CHARACTERS =>
          builder.advanceLexer()
        case ScalaXmlTokenTypes.XML_CHAR_ENTITY_REF =>
          builder.advanceLexer()
        case ScalaXmlTokenTypes.XML_ENTITY_REF_TOKEN => builder.advanceLexer()
        case _ =>
          if (isReturn) return
      }
      subparse()
    }
    subparse()
    contentMarker.drop()
    true
  }
}