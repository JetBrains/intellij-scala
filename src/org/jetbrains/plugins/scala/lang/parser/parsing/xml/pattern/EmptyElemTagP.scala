package org.jetbrains.plugins.scala.lang.parser.parsing.xml.pattern

import com.intellij.lang.PsiBuilder
import org.jetbrains.plugins.scala.lang.parser.ScalaElementTypes
import com.intellij.psi.xml.XmlTokenType

/**
* @author Alexander Podkhalyuzin
* Date: 18.04.2008
*/

/*
 * EmptyElemTagP ::= '<' Name [S]'/>'
 */

object EmptyElemTagP {
  def parse(builder: PsiBuilder): Boolean = {
    val tagMarker = builder.mark()
    builder.getTokenType match {
      case XmlTokenType.XML_START_TAG_START => {
        builder.advanceLexer()
      }
      case _ => {
        tagMarker.drop()
        return false
      }
    }
    builder.getTokenType match {
      case XmlTokenType.XML_NAME => {
        builder.advanceLexer()
      }
      case _ => builder error ErrMsg("xml.name.expected")
    }
    builder.getTokenType match {
      case XmlTokenType.XML_WHITE_SPACE => builder.advanceLexer()
      case _ =>
    }
    builder.getTokenType match {
      case XmlTokenType.XML_EMPTY_ELEMENT_END => {
        builder.advanceLexer()
        tagMarker.done(ScalaElementTypes.XML_EMPTY_TAG)
        return true
      }
      case _ => {
        tagMarker.rollbackTo
        return false
      }
    }
  }
}