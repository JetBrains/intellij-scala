package org.jetbrains.plugins.scala.lang.parser.parsing.expressions

import _root_.scala.collection.mutable._

import com.intellij.lang.PsiBuilder, org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.parser.ScalaElementTypes
import org.jetbrains.plugins.scala.lang.lexer.ScalaElementType
import org.jetbrains.plugins.scala.lang.parser.bnf.BNF
import com.intellij.psi.tree.TokenSet
import com.intellij.psi.tree.IElementType
import org.jetbrains.plugins.scala.lang.parser.util.ParserUtils
import org.jetbrains.plugins.scala.lang.parser.parsing.types._
import org.jetbrains.plugins.scala.lang.parser.parsing.patterns._

/** 
* @author Alexander Podkhalyuzin
* Date: 06.03.2008
*/

/*
 * Enumerator ::= Generator
 *              | Guard
 *              | 'val' Pattern1 '=' Expr
 */

object Enumerator {
  def parse(builder: PsiBuilder): Boolean = {
    val enumMarker = builder.mark

    def parseNonGuard(f: Boolean): Boolean = {
      if (!Pattern1.parse(builder))
        if (!f || !Guard.parse(builder, true)) {
          builder error ErrMsg("wrong.pattern")
          enumMarker.done(ScalaElementTypes.ENUMERATOR)
          return true
        }
        else {
          enumMarker.drop
          return true
        }
      builder.getTokenType match {
        case ScalaTokenTypes.tASSIGN => {
          builder.advanceLexer //Ate =
        }
        case ScalaTokenTypes.tCHOOSE => {
          enumMarker.rollbackTo
          return Generator parse builder
        }
        case _ => {
          if (!f) {
            builder error ErrMsg("choose.expected")
            enumMarker.done(ScalaElementTypes.ENUMERATOR)
          }
          else {
            enumMarker.rollbackTo
            Guard.parse(builder,true)
          }
          return true
        }
      }
      if (!Expr.parse(builder)) {
        builder error ErrMsg("wrong.expression")
      }
      enumMarker.done(ScalaElementTypes.ENUMERATOR)
      true
    }

    builder.getTokenType match {
      case ScalaTokenTypes.kIF => {
        Guard parse builder
        enumMarker.drop
        return true
      }
      case ScalaTokenTypes.kVAL => {
        builder.advanceLexer //Ate val
        return parseNonGuard(false)
      }
      case _ => {
        return parseNonGuard(true)
      }
    }
  }
}