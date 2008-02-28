package org.jetbrains.plugins.scala.lang.parser.parsing.types

import com.intellij.lang.PsiBuilder, org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.parser.ScalaElementTypes
import org.jetbrains.plugins.scala.lang.parser.bnf.BNF
import com.intellij.psi.tree.TokenSet
import com.intellij.psi.tree.IElementType
import org.jetbrains.plugins.scala.lang.parser.util.ParserUtils
import org.jetbrains.plugins.scala.lang.lexer.ScalaElementType
import org.jetbrains.plugins.scala.lang.parser.parsing.expressions.InfixTemplate
import org.jetbrains.plugins.scala.ScalaBundle
import org.jetbrains.plugins.scala.lang.parser.parsing.nl.LineTerminator

/** 
* Created by IntelliJ IDEA.
* User: Alexander.Podkhalyuz
* Date: 28.02.2008
* Time: 12:55:10
* To change this template use File | Settings | File Templates.
*/

/*
 * InfixType ::= CompoundType {id [nl] CompoundType}
 */

object InfixType {
  def parse(builder: PsiBuilder): Boolean = {
    val infixTypeMarker = builder.mark
    if (!CompoundType.parse(builder)) {
      infixTypeMarker.rollbackTo
      return false
    }
    while (builder.getTokenType == ScalaTokenTypes.tIDENTIFIER) {
      builder.advanceLexer //Ate id
      builder.getTokenType match {
        case ScalaTokenTypes.tLINE_TERMINATOR => {
          if (!LineTerminator(builder.getTokenText)) {
            builder error ScalaBundle.message("compound.type.expected", new Array[Object](0))
          }
          else {
            builder.advanceLexer //Ale nl
          }
        }
        case _ => {}
      }
      if (!CompoundType.parse(builder)) {
        builder error ScalaBundle.message("compound.type.expected", new Array[Object](0))
      }
    }
    infixTypeMarker.done(ScalaElementTypes.INFIX_TYPE)
    return true
  }
}