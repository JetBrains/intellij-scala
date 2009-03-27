package org.jetbrains.plugins.scala.lang.parser.parsing.xml

import com.intellij.lang.PsiBuilder, org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.parser.ScalaElementTypes
import com.intellij.psi.tree.TokenSet
import com.intellij.psi.tree.IElementType
import org.jetbrains.plugins.scala.lang.parser.util.ParserUtils
import org.jetbrains.plugins.scala.lang.lexer.ScalaElementType
import org.jetbrains.plugins.scala.ScalaBundle
import org.jetbrains.plugins.scala.lang.parser.parsing.expressions._
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