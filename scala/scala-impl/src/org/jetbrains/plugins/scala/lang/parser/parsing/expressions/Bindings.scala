package org.jetbrains.plugins.scala
package lang
package parser
package parsing
package expressions

import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.parser.parsing.builder.ScalaPsiBuilder

/**
* @author Alexander Podkhalyuzin
* Date: 06.03.2008
*/

/*
 * Bindings ::= '(' Binding {',' Binding } ')'
 */
object Bindings {

  def parse(builder: ScalaPsiBuilder): Boolean = {
    val bindingsMarker = builder.mark
    builder.getTokenType match {
      case ScalaTokenTypes.tLPARENTHESIS =>
        builder.advanceLexer() //Ate (
        builder.disableNewlines()
      case _ =>
        bindingsMarker.drop()
        return false
    }
    Binding parse builder
    while (builder.getTokenType == ScalaTokenTypes.tCOMMA && !builder.consumeTrailingComma(ScalaTokenTypes.tRPARENTHESIS)) {
      builder.advanceLexer() //Ate ,
      if (!Binding.parse(builder)) {
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