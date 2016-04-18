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

object Bindings extends Bindings {
  override protected val binding = Binding
}

trait Bindings {
  protected val binding: Binding

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
    binding parse builder
    while (builder.getTokenType == ScalaTokenTypes.tCOMMA) {
      builder.advanceLexer() //Ate ,
      if (!binding.parse(builder)) {
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
    bindingsMarker.done(ScalaElementTypes.PARAM_CLAUSE)
    pm.done(ScalaElementTypes.PARAM_CLAUSES)
    true
  }
}