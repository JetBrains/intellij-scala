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
 * Comment ::= <!-- comment -->
 */

object Comment {
  def parse(builder: PsiBuilder): Boolean = {
    val commentMarker = builder.mark
    builder.getTokenType match {
      case XmlTokenType.XML_COMMENT_START => builder.advanceLexer()
      case _ => {
        commentMarker.drop
        return false
      }
    }
    while (builder.getTokenType!=XmlTokenType.XML_COMMENT_END && builder.getTokenType != null) {
      if (builder.getTokenType == XmlTokenType.XML_BAD_CHARACTER) builder error ErrMsg("xml.wrong.character")
      builder.advanceLexer()
    }
    builder.getTokenType match {
      case XmlTokenType.XML_COMMENT_END => builder.advanceLexer()
      case _ => builder error ErrMsg("xml.comment.end.expected")
    }
    commentMarker.done(ScalaElementTypes.XML_COMMENT)
    return true
  }
}