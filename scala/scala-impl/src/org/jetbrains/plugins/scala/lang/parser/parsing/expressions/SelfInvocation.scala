package org.jetbrains.plugins.scala
package lang
package parser
package parsing
package expressions

import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.parser.parsing.builder.ScalaPsiBuilder

/**
 * @author Alexander Podkhalyuzin
 *  Date: 13.02.2008
 */

/*
 * SelfInvocation ::= 'this' ArgumentExprs {ArgumentExprs}
 */
object SelfInvocation {

  def parse(builder: ScalaPsiBuilder): Boolean = {
    val selfMarker = builder.mark
    builder.getTokenType match {
      case ScalaTokenTypes.kTHIS =>
        builder.advanceLexer() //Ate this
      case _ =>
        //error moved to ScalaAnnotator to differentiate with compiled files
        selfMarker.drop()
        return true
    }
    if (!ArgumentExprs.parse(builder)) {
      selfMarker.done(ScalaElementType.SELF_INVOCATION)
      return true
    }
    while (!builder.newlineBeforeCurrentToken && ArgumentExprs.parse(builder)) {}
    selfMarker.done(ScalaElementType.SELF_INVOCATION)
    true
  }
}