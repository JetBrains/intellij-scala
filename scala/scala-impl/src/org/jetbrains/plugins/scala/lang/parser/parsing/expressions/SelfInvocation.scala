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
object SelfInvocation extends ParsingRule {

  override def apply()(implicit builder: ScalaPsiBuilder): Boolean = {
    val selfMarker = builder.mark()
    builder.getTokenType match {
      case ScalaTokenTypes.kTHIS =>
        builder.advanceLexer() //Ate this
      case _ =>
        selfMarker.drop()
        return false
    }
    if (ArgumentExprs()) {
      while (!builder.newlineBeforeCurrentToken && ArgumentExprs()) {}
    }
    selfMarker.done(ScalaElementType.SELF_INVOCATION)
    true
  }
}