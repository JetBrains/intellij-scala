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
* Date: 08.02.2008
*/

/*
 *  Import ::= import ImportExpr { ,  ImportExpr}
 */

object Import {
  def parse(builder: PsiBuilder) {
    val importMarker = builder.mark
    builder.getTokenType match {
      case ScalaTokenTypes.kIMPORT => {
        builder.advanceLexer //Ate import
      }
      case _ => builder error ErrMsg("unreachable.error")
    }
    ImportExpr parse builder
    while (builder.getTokenType == ScalaTokenTypes.tCOMMA) {
      builder.advanceLexer //Ate ,
      ImportExpr parse builder
    }
    importMarker.done(ScalaElementTypes.IMPORT_STMT)
  }
}