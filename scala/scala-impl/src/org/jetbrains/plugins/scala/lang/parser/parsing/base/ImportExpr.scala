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
        // import a as b
        builder.getTokenType match {
          case ScalaTokenTypes.tIDENTIFIER | ScalaTokenTypes.tUNDER =>
            builder.advanceLexer() // ate id or _
          case _ =>
            builder error ErrMsg("identifier.or.wild.sign.expected")
        }
        importExprMarker.done(ScalaElementType.IMPORT_SELECTOR)
        val before = importExprMarker.precede()
        before.done(ScalaElementType.IMPORT_SELECTORS)
        before.precede().done(ScalaElementType.IMPORT_EXPR)
      } else {
        importExprMarker.done(ScalaElementType.IMPORT_EXPR)
      }
      return true
    }

    builder.advanceLexer() // ate .

    builder.getTokenType match {
      case ScalaTokenTypes.tUNDER => builder.advanceLexer() //Ate _
      case ScalaTokenTypes.tLBRACE => ImportSelectors()
      case ScalaTokenType.GivenKeyword =>
        builder.advanceLexer() // Ate given
        InfixType.parse(builder)
      case InScala3.orSource3(ScalaTokenTypes.tIDENTIFIER) =>
        if (!builder.tryParseSoftKeyword(ScalaTokenType.WildcardStar)) {
          val selectorsMarker = builder.mark()
          val selectorMarker = builder.mark()

          val sel = builder.mark()
          builder.advanceLexer() // Ate identifier
          sel.done(ScalaElementType.REFERENCE)

          // this should always succeed... otherwise StableId should have parsed it
          val didParseAs = builder.tryParseSoftKeyword(ScalaTokenType.AsKeyword)
          assert(didParseAs)
          builder.getTokenType match {
            case ScalaTokenTypes.tIDENTIFIER | ScalaTokenTypes.tUNDER =>
              builder.advanceLexer() // ate id or _
            case _ =>
              builder error ErrMsg("identifier.or.wild.sign.expected")
          }
          selectorMarker.done(ScalaElementType.IMPORT_SELECTOR)
          selectorsMarker.done(ScalaElementType.IMPORT_SELECTORS)
        }
      case _ => builder error ErrMsg("identifier.or.opening.brace.expected")
    }
    importExprMarker.done(ScalaElementType.IMPORT_EXPR)
    true
  }
}