package org.jetbrains.plugins.scala
package lang
package parser
package parsing
package base

import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.parser.parsing.builder.ScalaPsiBuilder
import org.jetbrains.plugins.scala.lang.parser.parsing.types.StableId

/**
* User: Alexander.Podkhalyuzin
*/

/*
 *  ImportExpr ::= StableId  '.'  (id | '_'  | ImportSelectors)
 */

object ImportExpr {
  def parse(builder: ScalaPsiBuilder): Boolean = {
    val importExprMarker = builder.mark
    if (!StableId.parse(builder, true, ScalaElementTypes.REFERENCE)) {
      builder error ErrMsg("identifier.expected")
      importExprMarker.drop
      return true
    }

    if (builder.getTokenType != ScalaTokenTypes.tDOT) {
      importExprMarker.done(ScalaElementTypes.IMPORT_EXPR)
      return true
    }
    builder.advanceLexer
    builder.getTokenType match {
      case ScalaTokenTypes.tUNDER => builder.advanceLexer //Ate _
      case ScalaTokenTypes.tLBRACE => ImportSelectors parse builder
      case _ => builder error ErrMsg("wrong.import.statment.end")
    }
    importExprMarker.done(ScalaElementTypes.IMPORT_EXPR)
    true
  }
}