package org.jetbrains.plugins.scala
package lang
package parser
package parsing
package xml

import com.intellij.psi.tree.TokenSet
import org.jetbrains.plugins.scala.lang.lexer.ScalaXmlTokenTypes
import org.jetbrains.plugins.scala.lang.parser.parsing.builder.ScalaPsiBuilder
import org.jetbrains.plugins.scala.lang.parser.util.ParserPatcher

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
  private val VALID_ATTRIBUTE_TOKENS = TokenSet.create(ScalaXmlTokenTypes.XML_ATTRIBUTE_VALUE_TOKEN, ScalaXmlTokenTypes.XML_CHAR_ENTITY_REF)
  
  def parse(builder: ScalaPsiBuilder): Boolean = {
    val attrValueMarker = builder.mark()
    val patcher = ParserPatcher.getSuitablePatcher(builder)
    
    builder.getTokenType match {
      case ScalaXmlTokenTypes.XML_ATTRIBUTE_VALUE_START_DELIMITER =>
        builder.advanceLexer()
        var patched = false
        while (VALID_ATTRIBUTE_TOKENS.contains(builder.getTokenType) || {patched = patcher parse builder; patched}) {
          if (!patched) builder.advanceLexer() else patched = false
        }
        builder.getTokenType match {
          case ScalaXmlTokenTypes.XML_ATTRIBUTE_VALUE_END_DELIMITER => builder.advanceLexer()
          case _ => builder error ErrMsg("xml.attribute.end.expected")
        }
      case _ =>
        if (!ScalaExpr.parse(builder) && !patcher.parse(builder)) {
          attrValueMarker.drop()
          return false
        }
    }
    attrValueMarker.drop()
    true
  }
}