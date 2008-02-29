package org.jetbrains.plugins.scala.lang.parser.parsing.patterns

import com.intellij.lang.PsiBuilder, org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.parser.ScalaElementTypes
import org.jetbrains.plugins.scala.lang.lexer.ScalaElementType
import org.jetbrains.plugins.scala.lang.parser.bnf.BNF
import com.intellij.psi.tree.TokenSet
import com.intellij.psi.tree.IElementType
import org.jetbrains.plugins.scala.lang.parser.util.ParserUtils
import org.jetbrains.plugins.scala.lang.parser.parsing.types._
import org.jetbrains.plugins.scala.lang.parser.parsing.top.template._
import org.jetbrains.plugins.scala.lang.parser.parsing.expressions._
import org.jetbrains.plugins.scala.lang.parser.bnf._
import org.jetbrains.plugins.scala.lang.parser.parsing.nl.LineTerminator

/** 
* Created by IntelliJ IDEA.
* User: Alexander.Podkhalyuz
* Date: 29.02.2008
* Time: 18:45:12
* To change this template use File | Settings | File Templates.
*/

object Patterns {
  def parse(builder: PsiBuilder): Boolean = {
    val patternsMarker = builder.mark
    if (!Pattern.parse(builder)) {
      patternsMarker.rollbackTo
      return false
    }
    builder.getTokenType match {
      case ScalaTokenTypes.tCOMMA => {
        builder.advanceLexer //Ate ,
        while (Pattern.parse(builder)) {
          builder.getTokenType match {
            case ScalaTokenTypes.tCOMMA => {
              builder.advanceLexer //Ate ,
            }
            case _ => {
              patternsMarker.done(ScalaElementTypes.PATTERNS)
              return true
            }
          }
        }
        patternsMarker.done(ScalaElementTypes.PATTERNS)
        return true
      }
      case _ => {
        patternsMarker.rollbackTo
        return false
      }
    }
  }
}