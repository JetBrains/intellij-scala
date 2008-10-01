package org.jetbrains.plugins.scala.lang.parser.parsing.base

import com.intellij.lang.PsiBuilder
import lexer.ScalaTokenTypes

/**
* @author Alexander Podkhalyuzin
* Date: 11.02.2008
*/

object ImportSelector {
  def parse(builder: PsiBuilder): Boolean = {
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
            builder error ErrMsg("identifier.or.wild.sign.expected")
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