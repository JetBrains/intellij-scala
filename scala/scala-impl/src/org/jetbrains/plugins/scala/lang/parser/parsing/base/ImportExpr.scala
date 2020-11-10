package org.jetbrains.plugins.scala
package lang
package parser
package parsing
package base

import org.jetbrains.plugins.scala.lang.lexer.{ScalaTokenType, ScalaTokenTypes}
import org.jetbrains.plugins.scala.lang.parser.parsing.builder.ScalaPsiBuilder
import org.jetbrains.plugins.scala.lang.parser.parsing.types.StableId
import org.jetbrains.plugins.scala.lang.parser.util.InScala3

/**
* User: Alexander.Podkhalyuzin
*/

/*
 *  ImportExpr ::= StableId  '.'  (id | '_'  | ImportSelectors)
 */

object ImportExpr extends ParsingRule {
  override def apply()(implicit builder: ScalaPsiBuilder): Boolean = {
    val importExprMarker = builder.mark
    if (!StableId.parse(builder, forImport = true, ScalaElementType.REFERENCE)) {
      builder error ErrMsg("identifier.expected")
      importExprMarker.drop()
      return true
    }

    if (builder.getTokenType != ScalaTokenTypes.tDOT) {
      importExprMarker.done(ScalaElementType.IMPORT_EXPR)
      return true
    }
    builder.advanceLexer()
    builder.getTokenType match {
      case ScalaTokenTypes.tUNDER => builder.advanceLexer() //Ate _
      case ScalaTokenTypes.tLBRACE => ImportSelectors()
      case InScala3(ScalaTokenType.GivenKeyword) => builder.advanceLexer() // Ate given
      case _ => builder error ErrMsg("identifier.or.opening.brace.expected")
    }
    importExprMarker.done(ScalaElementType.IMPORT_EXPR)
    true
  }
}