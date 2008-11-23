package org.jetbrains.plugins.scala.lang.parser.parsing.types

import com.intellij.lang.PsiBuilder, org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.parser.ScalaElementTypes
import com.intellij.psi.tree.TokenSet
import com.intellij.psi.tree.IElementType
import org.jetbrains.plugins.scala.lang.parser.util.ParserUtils
import org.jetbrains.plugins.scala.lang.lexer.ScalaElementType
import org.jetbrains.plugins.scala.ScalaBundle
import org.jetbrains.plugins.scala.lang.parser.parsing.nl.LineTerminator

/** 
* @author Alexander Podkhalyuzin
* Date: 28.02.2008
*/

/*
 * ExistentialClause ::= 'forSome' '{' ExistentialDcl {semi ExistentialDcl} '}'
 */

object ExistentialClause {
  def parse(builder: PsiBuilder) : Boolean = {
    val existMarker = builder.mark
    builder.getTokenType match {
      case ScalaTokenTypes.kFOR_SOME => {
        builder.advanceLexer //Ate forSome
      }
      case _ => {
        existMarker.drop
        return false
      }
    }
    builder.getTokenType match {
      case ScalaTokenTypes.tLBRACE => {
        builder.advanceLexer //Ate {
      }
      case _ => {
        builder error ScalaBundle.message("existential.block.expected")
        existMarker.done(ScalaElementTypes.EXISTENTIAL_CLAUSE)
        return true
      }
    }
    ExistentialDclSeq parse builder
    builder.getTokenType match {
      case ScalaTokenTypes.tRBRACE => builder.advanceLexer //Ate }
      case _ => builder error ScalaBundle.message("rbrace.expected",new Array[Object](0))
    }
    existMarker.done(ScalaElementTypes.EXISTENTIAL_CLAUSE)
    return true
  }
}