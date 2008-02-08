package org.jetbrains.plugins.scala.lang.parser.parsing.base

import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.lexer.ScalaElementType
import org.jetbrains.plugins.scala.lang.parser.ScalaElementTypes
import org.jetbrains.plugins.scala.lang.parser.parsing.types.StableId
import org.jetbrains.plugins.scala.lang.parser.parsing.types.StableIdInImport
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
* Created by IntelliJ IDEA.
* User: Alexander.Podkhalyuz
* Date: 08.02.2008
* Time: 17:00:55
* To change this template use File | Settings | File Templates.
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
      case _ => builder error ScalaBundle.message("unreachable.error", new Array[Object](0))
    }
    ParserUtils.listOfSmth(builder, ImportExpr, ScalaTokenTypes.tCOMMA, ScalaElementTypes.IMPORT_EXPRS)
    importMarker.done(ScalaElementTypes.IMPORT_STMT)
  }
}