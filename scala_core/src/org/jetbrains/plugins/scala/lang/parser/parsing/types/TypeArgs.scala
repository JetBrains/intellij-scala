package org.jetbrains.plugins.scala.lang.parser.parsing.types

import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.lexer.ScalaElementType
import org.jetbrains.plugins.scala.lang.parser.ScalaElementTypes
import org.jetbrains.plugins.scala.lang.parser.parsing.expressions.Exprs
import org.jetbrains.plugins.scala.lang.parser.util.ParserUtils
import org.jetbrains.plugins.scala.util.DebugPrint
import org.jetbrains.plugins.scala.lang.parser.bnf.BNF
import org.jetbrains.plugins.scala.lang.parser.parsing.expressions.ArgumentExprs

import com.intellij.psi.tree.TokenSet
import com.intellij.lang.PsiBuilder
import com.intellij.psi.tree.IElementType

/** 
* @author Alexander Podkhalyuzin
* Date: 15.02.2008
*/

/*
 *  typeArgs ::= '[' Types ']'
 */

object TypeArgs {
  def parse(builder: PsiBuilder): Boolean = {
    val typeArgsMarker = builder.mark
    builder.getTokenType match {
      case ScalaTokenTypes.tLSQBRACKET => {
        builder.advanceLexer //Ate [
      }
      case _ => {
        typeArgsMarker.rollbackTo
        return false
      }
    }
    val (is,_) = Types.parse(builder)
    if (!is) {
      builder error ScalaBundle.message("wrong.type", new Array[Object](0))
    }
    builder.getTokenType match {
      case ScalaTokenTypes.tRSQBRACKET => {
        builder.advanceLexer //Ate ]
      }
      case _ => builder error ScalaBundle.message("rsqbracket.expected", new Array[Object](0))
    }
    typeArgsMarker.done(ScalaElementTypes.TYPE_ARGS)
    return true
  }
}