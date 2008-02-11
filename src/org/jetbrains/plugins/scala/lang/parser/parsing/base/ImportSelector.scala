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
* Time: 17:39:04
* To change this template use File | Settings | File Templates.
*/

object ImportSelector {
  def parse(builder:PsiBuilder): Boolean = {
    val importSelectorMarker = builder.mark
    builder.getTokenType match {
      case ScalaTokenTypes.tIDENTIFIER => {
        val sel = builder.mark()
        builder.advanceLexer // Ate identifier
        sel.done(ScalaElementTypes.REFERENCE)
      }
      case _ => {
        importSelectorMarker.drop
        return false
      }
    }
    builder.getTokenType match {
      case ScalaTokenTypes.tFUNTYPE => {
        builder.advanceLexer //Ate =>
        builder.getTokenType match {
          case ScalaTokenTypes.tUNDER | ScalaTokenTypes.tIDENTIFIER => {
            builder.advanceLexer //Ate _ | identifier
            importSelectorMarker.done(ScalaElementTypes.IMPORT_SELECTOR)
            return true
          }
          case _ => {
            builder error ScalaBundle.message("identifier.or.wild.sign.expected", new Array[Object](0))
            importSelectorMarker.done(ScalaElementTypes.IMPORT_SELECTOR)
            return true
          }
        }
      }
      case _ => {
        importSelectorMarker.done(ScalaElementTypes.IMPORT_SELECTOR)
        return true
      }
    }
  }
}