package org.jetbrains.plugins.scala
package lang
package parser
package parsing
package expressions

import org.jetbrains.plugins.scala.lang.lexer.{ScalaTokenType, ScalaTokenTypes}
import org.jetbrains.plugins.scala.lang.parser.parsing.builder.ScalaPsiBuilder

/*
 * Bindings ::= '(' Binding {',' Binding } ')'
 */
object Bindings extends ParsingRule {
  override def parse(implicit builder: ScalaPsiBuilder): Boolean = {
    val bindingsMarker = builder.mark()
    builder.getTokenType match {
      case ScalaTokenTypes.tLPARENTHESIS =>
        builder.advanceLexer() //Ate (
        builder.disableNewlines()
      case _ =>
        bindingsMarker.drop()
        return false
    }

    if (builder.isScala3) {
      builder.tryParseSoftKeyword(ScalaTokenType.UsingKeyword)
    }

    Binding()
    while (builder.getTokenType == ScalaTokenTypes.tCOMMA && !builder.consumeTrailingComma(ScalaTokenTypes.tRPARENTHESIS)) {
      builder.advanceLexer() //Ate ,
      if (!Binding()) {
        builder error ErrMsg("wrong.binding")
      }
    }
    builder.getTokenType match {
      case ScalaTokenTypes.tRPARENTHESIS =>
        builder.advanceLexer() //Ate )
        builder.restoreNewlinesState()
      case _ =>
        builder.restoreNewlinesState()
        bindingsMarker.rollbackTo()
        return false
    }
    val pm = bindingsMarker.precede
    bindingsMarker.done(ScalaElementType.PARAM_CLAUSE)
    pm.done(ScalaElementType.PARAM_CLAUSES)
    true
  }
}