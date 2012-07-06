package org.jetbrains.plugins.scala
package lang
package parser
package parsing
package xml

import com.intellij.lang.PsiBuilder
import com.intellij.psi.xml.XmlTokenType
import builder.ScalaPsiBuilder
import parser.util.ParserPatcher

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
      case XmlTokenType.XML_DATA_CHARACTERS => {
        builder.advanceLexer()
      }
      case XmlTokenType.XML_CHAR_ENTITY_REF => {
        builder.advanceLexer()
      }
      case _ =>
    }
    
    val patcher = ParserPatcher.getSuitablePatcher(builder)
    
    def subparse() {
      var isReturn = false
      if (!XmlContent.parse(builder) &&
              !Reference.parse(builder) &&
              !ScalaExpr.parse(builder) && !patcher.parse(builder)) isReturn = true
      builder.getTokenType match {
        case XmlTokenType.XML_DATA_CHARACTERS => {
          builder.advanceLexer()
        }
        case XmlTokenType.XML_CHAR_ENTITY_REF => {
          builder.advanceLexer()
        }
        case XmlTokenType.XML_ENTITY_REF_TOKEN => builder.advanceLexer()
        case _ => {
          if (isReturn) return
        }
      }
      subparse()
    }
    subparse()
    contentMarker.drop()
    true
  }
}