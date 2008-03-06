package org.jetbrains.plugins.scala.lang.parser.parsing.expressions

import com.intellij.lang.PsiBuilder, org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.parser.ScalaElementTypes
import org.jetbrains.plugins.scala.lang.lexer.ScalaElementType
import org.jetbrains.plugins.scala.lang.parser.bnf.BNF
import com.intellij.psi.tree.TokenSet
import com.intellij.psi.tree.IElementType
import org.jetbrains.plugins.scala.lang.parser.util.ParserUtils
import org.jetbrains.plugins.scala.lang.parser.parsing.types._

/** 
* Created by IntelliJ IDEA.
* User: Alexander.Podkhalyuz
* Date: 06.03.2008
* Time: 12:48:28
* To change this template use File | Settings | File Templates.
*/

/*
 * Bindings ::= '(' Binding {',' Binding } ')'
 */

object Bindings {
  def parse(builder: PsiBuilder): Boolean = {
    val bindingsMarker = builder.mark
    builder.getTokenType match {
      case ScalaTokenTypes.tLPARENTHESIS => {
        builder.advanceLexer //Ate (
      }
      case _ => {
        bindingsMarker.drop
        return false
      }
    }
    if (!Binding.parse(builder)) {
      bindingsMarker.rollbackTo
      return false
    }
    while (builder.getTokenType == ScalaTokenTypes.tCOMMA) {
      builder.advanceLexer //Ate ,
      if (!Binding.parse(builder)) {
        builder error ScalaBundle.message("wrong.binding", new Array[Object](0))
      }
    }
    builder.getTokenType match {
      case ScalaTokenTypes.tRPARENTHESIS => {
        builder.advanceLexer //Ate )
      }
      case _ => {
        builder error ScalaBundle.message("rparenthesis.expected", new Array[Object](0))
      }
    }
    bindingsMarker.done(ScalaElementTypes.BINDINGS)
    return true
  }
}