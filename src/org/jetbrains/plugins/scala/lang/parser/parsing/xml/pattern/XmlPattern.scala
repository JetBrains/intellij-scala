package org.jetbrains.plugins.scala
package lang
package parser
package parsing
package xml.pattern

import com.intellij.lang.PsiBuilder, org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.parser.ScalaElementTypes
/**
* @author Alexander Podkhalyuzin
* Date: 18.04.2008
*/

/*
 * XmlPattern ::= EmptyElemTagP
 *              | STagP ContentP ETagP
 */

object XmlPattern {
  def parse(builder: PsiBuilder): Boolean = {
    val patternMarker = builder.mark()
    if (EmptyElemTagP.parse(builder)) {
      patternMarker.done(ScalaElementTypes.XML_PATTERN)
      return true
    }
    if (!STagP.parse(builder)) {
      patternMarker.drop
      return false
    }
    ContentP parse builder
    if (!ETagP.parse(builder)) builder error ErrMsg("xml.end.tag.expected")
    patternMarker.done(ScalaElementTypes.XML_PATTERN)
    while (builder.getTokenType == ScalaTokenTypes.tLINE_TERMINATOR) {
      builder.advanceLexer
    }
    return true
  }
}