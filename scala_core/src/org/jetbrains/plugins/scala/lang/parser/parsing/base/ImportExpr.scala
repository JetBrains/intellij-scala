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
import org.jetbrains.plugins.scala.ScalaBundle
import ScalaElementTypes._

import com.intellij.psi.tree.TokenSet
import com.intellij.lang.PsiBuilder
import com.intellij.psi.tree.IElementType

/** 
* User: Alexander.Podkhalyuzin
*/

/*
 *  ImportExpr ::= StableId  '.'  (id | '_'  | ImportSelectors)
 */

object ImportExpr {
  def parse(builder: PsiBuilder): Boolean = {
    val importExprMarker = builder.mark
    if (!StableId.parse(builder, true, REFERENCE)) {
      builder error ErrMsg("identifier.expected")
    }

    if (builder.getTokenType != ScalaTokenTypes.tDOT) {
      importExprMarker.done(IMPORT_EXPR)
      return true
    }
    builder.advanceLexer

    builder.getTokenType() match {
      case ScalaTokenTypes.tUNDER => builder.advanceLexer //Ate _
      case ScalaTokenTypes.tLBRACE => ImportSelectors parse builder
      case _ => builder error ErrMsg("wrong.import.statment.end")
    }
    importExprMarker.done(IMPORT_EXPR)
    true
  }
}