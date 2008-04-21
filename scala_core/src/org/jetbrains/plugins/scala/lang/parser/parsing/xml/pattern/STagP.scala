package org.jetbrains.plugins.scala.lang.parser.parsing.xml.pattern

import com.intellij.lang.PsiBuilder, org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.parser.ScalaElementTypes
import org.jetbrains.plugins.scala.lang.parser.bnf.BNF
import com.intellij.psi.tree.TokenSet
import com.intellij.psi.tree.IElementType
import org.jetbrains.plugins.scala.lang.parser.util.ParserUtils
import org.jetbrains.plugins.scala.lang.lexer.ScalaElementType
import org.jetbrains.plugins.scala.ScalaBundle
import org.jetbrains.plugins.scala.lang.parser.parsing.expressions._
import org.jetbrains.plugins.scala.lang.parser.parsing.xml._
import com.intellij.psi.xml.XmlTokenType

/**
* @author Alexander Podkhalyuzin
* Date: 18.04.2008
*/

/*
 * STagP ::= < Name [S] >
 */

object STagP {
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
      case _ => builder error ErrMsg("xml.name.expected") //TODO: add this error
    }
    builder.getTokenType match {
      case XmlTokenType.XML_TAG_END => {
        builder.advanceLexer()
        tagMarker.drop //todo: should be done
        return true
      }
      case _ => {
        builder error ErrMsg("xml.tag.end.expected") //TODO
        tagMarker.drop //todo
        return true
      }
    }
  }
}