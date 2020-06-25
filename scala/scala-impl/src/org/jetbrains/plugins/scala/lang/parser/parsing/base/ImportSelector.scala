package org.jetbrains.plugins.scala
package lang
package parser
package parsing
package base

import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.parser.parsing.builder.ScalaPsiBuilder

/**
* @author Alexander Podkhalyuzin
* Date: 11.02.2008
*/

object ImportSelector {
  def parse(builder: ScalaPsiBuilder): Boolean = {
    val importSelectorMarker = builder.mark
    builder.getTokenType match {
      case ScalaTokenTypes.tIDENTIFIER =>
        val sel = builder.mark()
        builder.advanceLexer() // Ate identifier
        sel.done(ScalaElementType.REFERENCE)
      case _ =>
        importSelectorMarker.drop()
        return false
    }
    builder.getTokenType match {
      case ScalaTokenTypes.tFUNTYPE =>
        builder.advanceLexer() //Ate =>
        builder.getTokenType match {
          case ScalaTokenTypes.tUNDER | ScalaTokenTypes.tIDENTIFIER => {
            builder.advanceLexer() //Ate _ | identifier
            importSelectorMarker.done(ScalaElementType.IMPORT_SELECTOR)
            return true
          }
          case _ => {
            builder error ErrMsg("identifier.or.wild.sign.expected")
            importSelectorMarker.done(ScalaElementType.IMPORT_SELECTOR)
            return true
          }
        }
      case _ =>
        importSelectorMarker.done(ScalaElementType.IMPORT_SELECTOR)
        return true
    }
  }
}