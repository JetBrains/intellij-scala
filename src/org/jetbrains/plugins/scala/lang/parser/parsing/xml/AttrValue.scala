package org.jetbrains.plugins.scala.lang.parser.parsing.xml

import com.intellij.lang.PsiBuilder
import com.intellij.psi.xml.XmlTokenType

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
  def parse(builder: PsiBuilder): Boolean = {
    val attrValueMarker = builder.mark()
    builder.getTokenType match {
      case XmlTokenType.XML_ATTRIBUTE_VALUE_START_DELIMITER => {
        builder.advanceLexer()
        builder.getTokenType match {
          case XmlTokenType.XML_ATTRIBUTE_VALUE_TOKEN => builder.advanceLexer()
          case _ =>
        }
        builder.getTokenType match {
          case XmlTokenType.XML_ATTRIBUTE_VALUE_END_DELIMITER => builder.advanceLexer()
          case _ => builder error ErrMsg("xml.attribute.end.expected")
        }
      }
      case _ => {
        if (!ScalaExpr.parse(builder)) {
          attrValueMarker.drop
          return false
        }
      }
    }
    attrValueMarker.drop
    return true
  }
}