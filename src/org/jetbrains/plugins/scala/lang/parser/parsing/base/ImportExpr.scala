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
import org.jetbrains.plugins.scala.ScalaBundle

import com.intellij.psi.tree.TokenSet
import com.intellij.lang.PsiBuilder
import com.intellij.psi.tree.IElementType

/** 
* Created by IntelliJ IDEA.
* User: Alexander.Podkhalyuz
* Date: 11.02.2008
* Time: 16:40:05
* To change this template use File | Settings | File Templates.
*/

/*
 *  ImportExpr ::= StableId  '.'  (id | '_'  | ImportSelectors)
 */

object ImportExpr {
  def parse(builder: PsiBuilder): Boolean = {
    val importExprMarker = builder.mark
    builder.getTokenType match {
      case ScalaTokenTypes.tIDENTIFIER => {
        StableIdInImport parse builder
      }
      case _ => {
        builder error ScalaBundle.message("identifier.expected",new Array[Object](0))
        importExprMarker.done(ScalaElementTypes.IMPORT_EXPR)
        return true
      }
    }
    builder.getTokenType match {
      case ScalaTokenTypes.tDOT => builder.advanceLexer //Ate .
      case _ => {
        builder error ScalaBundle.message("dot.expected",new Array[Object](0))
        importExprMarker.done(ScalaElementTypes.IMPORT_EXPR)
        return true
      }
    }
    val endMarker = builder.mark()
    builder.getTokenType() match {
      case ScalaTokenTypes.tIDENTIFIER => {
        builder.advanceLexer // Ate identifier
        endMarker.done(ScalaElementTypes.REFERENCE)
        importExprMarker.done(ScalaElementTypes.IMPORT_EXPR)
        return true
      }
      case ScalaTokenTypes.tUNDER => {
        endMarker.drop()
        builder.advanceLexer //Ate _
        importExprMarker.done(ScalaElementTypes.IMPORT_EXPR)
        return true
      }
      case ScalaTokenTypes.tLBRACE => {
        endMarker.drop
        ImportSelectors parse builder
        importExprMarker.done(ScalaElementTypes.IMPORT_EXPR)
        return true
      }
      case _ => {
        endMarker.drop
        builder error ScalaBundle.message("wrong.import.statment.end", new Array[Object](0))
        importExprMarker.done(ScalaElementTypes.IMPORT_EXPR)
        return true
      }
    }
  }
}