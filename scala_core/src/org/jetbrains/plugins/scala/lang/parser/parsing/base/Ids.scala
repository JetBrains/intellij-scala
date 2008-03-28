package org.jetbrains.plugins.scala.lang.parser.parsing.base

import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.lexer.ScalaElementType
import org.jetbrains.plugins.scala.lang.parser.ScalaElementTypes
import org.jetbrains.plugins.scala.lang.parser.parsing.types.StableId

import org.jetbrains.plugins.scala.lang.parser.parsing.expressions.Exprs
import org.jetbrains.plugins.scala.lang.parser.util.ParserUtils
import org.jetbrains.plugins.scala.util.DebugPrint
import org.jetbrains.plugins.scala.lang.parser.parsing.types.StableId
import org.jetbrains.plugins.scala.lang.parser.bnf.BNF
import org.jetbrains.plugins.scala.lang.parser.parsing.types.Type

import com.intellij.psi.tree.TokenSet
import com.intellij.lang.PsiBuilder
import com.intellij.psi.tree.IElementType

/** 
* @author Alexander Podkhalyuzin
* Date: 15.02.2008
*/

/*
 *  ids ::= id { ,  id}
 */

object Ids {
  def parse(builder: PsiBuilder): Boolean = {
    val idListMarker = builder.mark
    builder.getTokenType match {
      case ScalaTokenTypes.tIDENTIFIER => {
        builder.advanceLexer //Ate identifier
      }
      case _ => {
        idListMarker.rollbackTo
        return false
      }
    }
    var hasIds = false
    while (builder.getTokenType == ScalaTokenTypes.tCOMMA) {
      hasIds = true
      builder.advanceLexer //Ate ,
      builder.getTokenType match {
        case ScalaTokenTypes.tIDENTIFIER => {
          builder.advanceLexer //Ate identifier
        }
        case _ => {
          builder error ErrMsg("identifier.expected")
          idListMarker.done(ScalaElementTypes.IDENTIFIER_LIST)
          return true
        }
      }
    }
    if (hasIds) {
      idListMarker.done(ScalaElementTypes.IDENTIFIER_LIST)
      return true
    }
    idListMarker.drop
    return true
  }
}