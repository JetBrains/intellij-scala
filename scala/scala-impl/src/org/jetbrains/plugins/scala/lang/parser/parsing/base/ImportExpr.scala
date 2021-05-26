package org.jetbrains.plugins.scala
package lang
package parser
package parsing
package base

import org.jetbrains.plugins.scala.lang.lexer.{ScalaTokenType, ScalaTokenTypes}
import org.jetbrains.plugins.scala.lang.parser.parsing.builder.ScalaPsiBuilder
import org.jetbrains.plugins.scala.lang.parser.parsing.types.{InfixType, StableId}
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
      if (builder.isScala3orSource3 && builder.tryParseSoftKeyword(ScalaTokenType.AsKeyword)) {
        builder.getTokenType match {
          case ScalaTokenTypes.tIDENTIFIER | ScalaTokenTypes.tUNDER =>
            builder.advanceLexer() // ate id or _
          case _ =>
            builder error ErrMsg("identifier.or.wild.sign.expected")
        }
      }
      importExprMarker.done(ScalaElementType.IMPORT_EXPR)
      return true
    }

    builder.advanceLexer() // ate .

    builder.getTokenType match {
      case ScalaTokenTypes.tUNDER => builder.advanceLexer() //Ate _ or *
      case InScala3.orSource3(_) if builder.tryParseSoftKeyword(ScalaTokenType.WildcardStar) =>
      case ScalaTokenTypes.tLBRACE => ImportSelectors()
      case ScalaTokenType.GivenKeyword =>
        builder.advanceLexer() // Ate given
        InfixType.parse(builder)
      case _ => builder error ErrMsg("identifier.or.opening.brace.expected")
    }
    importExprMarker.done(ScalaElementType.IMPORT_EXPR)
    true
  }
}