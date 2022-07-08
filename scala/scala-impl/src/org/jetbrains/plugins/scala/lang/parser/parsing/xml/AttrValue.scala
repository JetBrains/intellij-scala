package org.jetbrains.plugins.scala
package lang
package parser
package parsing
package xml

import com.intellij.psi.tree.TokenSet
import org.jetbrains.plugins.scala.lang.lexer.ScalaXmlTokenTypes
import org.jetbrains.plugins.scala.lang.parser.parsing.builder.ScalaPsiBuilder

/*
 * AttrValue ::= " {CharQ | CharRef} "
 *             | ' {CharA | CharRef} '
 *             | ScalaExpr
 */

object AttrValue extends ParsingRule {
  private val VALID_ATTRIBUTE_TOKENS = TokenSet.create(ScalaXmlTokenTypes.XML_ATTRIBUTE_VALUE_TOKEN, ScalaXmlTokenTypes.XML_CHAR_ENTITY_REF)

  override def parse(implicit builder: ScalaPsiBuilder): Boolean = {
    val attrValueMarker = builder.mark()
    builder.getTokenType match {
      case ScalaXmlTokenTypes.XML_ATTRIBUTE_VALUE_START_DELIMITER =>
        builder.advanceLexer()
        var patched = false
        while (VALID_ATTRIBUTE_TOKENS.contains(builder.getTokenType) || {patched = builder.skipExternalToken(); patched}) {
          if (!patched) builder.advanceLexer() else patched = false
        }
        builder.getTokenType match {
          case ScalaXmlTokenTypes.XML_ATTRIBUTE_VALUE_END_DELIMITER => builder.advanceLexer()
          case _ => builder error ErrMsg("xml.attribute.end.expected")
        }
      case _ =>
        if (ScalaExpr() || builder.skipExternalToken()) {
        } else {
          attrValueMarker.drop()
          return false
        }
    }
    attrValueMarker.drop()
    true
  }
}