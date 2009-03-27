package org.jetbrains.plugins.scala.lang.parser.parsing.xml.pattern

import com.intellij.lang.PsiBuilder, org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.parser.ScalaElementTypes
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