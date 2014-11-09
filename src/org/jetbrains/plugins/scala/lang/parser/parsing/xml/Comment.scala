package org.jetbrains.plugins.scala
package lang
package parser
package parsing
package xml

import com.intellij.psi.xml.XmlTokenType
import org.jetbrains.plugins.scala.lang.parser.parsing.builder.ScalaPsiBuilder

/**
* @author Alexander Podkhalyuzin
* Date: 18.04.2008
*/

/*
 * Comment ::= <!-- comment -->
 */

object Comment {
  def parse(builder: ScalaPsiBuilder): Boolean = {
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