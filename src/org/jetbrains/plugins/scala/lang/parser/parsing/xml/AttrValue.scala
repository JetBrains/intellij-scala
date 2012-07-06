package org.jetbrains.plugins.scala
package lang
package parser
package parsing
package xml

import com.intellij.psi.xml.XmlTokenType
import builder.ScalaPsiBuilder
import parser.util.ParserPatcher
import com.intellij.psi.tree.TokenSet

/**
* @author Alexander Podkhalyuzin
* Date: 18.04.2008
*/

/*
 * AttrValue ::= " {CharQ | CharRef} "
 *             | ' {CharA | CharRef} '
 *             | ScalaExpr
 */

object AttrValue {
  private val VALID_ATTRIBUTE_TOKENS = TokenSet.create(XmlTokenType.XML_ATTRIBUTE_VALUE_TOKEN, XmlTokenType.XML_CHAR_ENTITY_REF)
  
  def parse(builder: ScalaPsiBuilder): Boolean = {
    val attrValueMarker = builder.mark()
    val patcher = ParserPatcher.getSuitablePatcher(builder)
    
    builder.getTokenType match {
      case XmlTokenType.XML_ATTRIBUTE_VALUE_START_DELIMITER => {
        builder.advanceLexer()
        while (VALID_ATTRIBUTE_TOKENS.contains(builder.getTokenType)) {
          builder.advanceLexer()
        }
        while (patcher.parse(builder)){}
        builder.getTokenType match {
          case XmlTokenType.XML_ATTRIBUTE_VALUE_END_DELIMITER => builder.advanceLexer()
          case _ => builder error ErrMsg("xml.attribute.end.expected")
        }
      }
      case _ => {
        if (!ScalaExpr.parse(builder) && !patcher.parse(builder)) {
          attrValueMarker.drop()
          return false
        }
      }
    }
    attrValueMarker.drop()
    true
  }
}