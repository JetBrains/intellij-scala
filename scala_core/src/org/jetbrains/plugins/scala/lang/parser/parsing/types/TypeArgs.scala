package org.jetbrains.plugins.scala.lang.parser.parsing.types

import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.lexer.ScalaElementType
import org.jetbrains.plugins.scala.lang.parser.ScalaElementTypes
import org.jetbrains.plugins.scala.lang.parser.util.ParserUtils._
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
  def parse(builder: PsiBuilder): Boolean = withMarker(ScalaElementTypes.TYPE_ARGS, builder) {
    builder.getTokenType match {
      case ScalaTokenTypes.tLSQBRACKET => {
        builder.advanceLexer //Ate [
        if (Type.parse(builder)) {
          var parsedType = true
          while (builder.getTokenType == ScalaTokenTypes.tCOMMA && parsedType) {
            builder.advanceLexer
            parsedType = Type.parse(builder)
            if (!parsedType) builder error ScalaBundle.message("wrong.type", new Array[Object](0))
          }
        } else builder error ScalaBundle.message("wrong.type", new Array[Object](0))

        builder.getTokenType match {
          case ScalaTokenTypes.tRSQBRACKET => {
            builder.advanceLexer //Ate ]
          }
          case _ => builder error ScalaBundle.message("rsqbracket.expected", new Array[Object](0))
        }
        true
      }
      case _ => false
    }
  }
}