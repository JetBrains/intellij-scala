package org.jetbrains.plugins.scala.lang.parser.parsing.expressions

import _root_.scala.collection.mutable._

import com.intellij.lang.PsiBuilder, org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.parser.ScalaElementTypes
import org.jetbrains.plugins.scala.lang.lexer.ScalaElementType
import org.jetbrains.plugins.scala.lang.parser.bnf.BNF
import com.intellij.psi.tree.TokenSet
import com.intellij.psi.tree.IElementType
import org.jetbrains.plugins.scala.util._
import org.jetbrains.plugins.scala.lang.parser.util.ParserUtils
import org.jetbrains.plugins.scala.lang.parser.parsing.types._
import org.jetbrains.plugins.scala.lang.parser.parsing.patterns._
import org.jetbrains.plugins.scala.lang.parser.parsing.top.template._
import org.jetbrains.plugins.scala.lang.parser.parsing.base._
import org.jetbrains.plugins.scala.lang.parser.parsing.top._

/** 
* @author Alexander Podkhalyuzin
* Date: 06.03.2008
*/

/*
 * Block ::= {BlockStat semi}[ResultExpr]
 */

object Block {

  def parse(builder: PsiBuilder): Boolean = {
    while (!ResultExpr.parse(builder) && BlockStat.parse(builder)) {}
    return true
  }

  private def parseImpl(builder: PsiBuilder): Int = {
    var i: Int = 0;
    while (!ResultExpr.parse(builder) && BlockStat.parse(builder)) {
      val t = builder.getTokenType
      if (!(t == ScalaTokenTypes.tLINE_TERMINATOR || t == ScalaTokenTypes.tSEMICOLON)) {
        i = i + 1;
      }
    }
    i
  }

  def parse(builder: PsiBuilder, hasBrace: Boolean): Boolean = {
    if (hasBrace) {
      val blockMarker = builder.mark
      builder.getTokenType match {
        case ScalaTokenTypes.tLBRACE => {
          builder.advanceLexer
        }
        case _ => {
          blockMarker.drop
          return false
        }
      }
      parse(builder)
      builder.getTokenType match {
        case ScalaTokenTypes.tRBRACE => {
          builder.advanceLexer
        }
        case _ => {
          builder error ErrMsg("rbrace.expected")
        }
      }
      blockMarker.done(ScalaElementTypes.BLOCK_EXPR)
    }
    else {
      val bm = builder.mark()
      if (parseImpl(builder) > 1) {
        bm.done(ScalaElementTypes.BLOCK)
      } else {
        bm.drop
      }
    }
    return true
  }
}