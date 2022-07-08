package org.jetbrains.plugins.scala
package lang
package parser
package parsing
package expressions

import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.parser.parsing.builder.ScalaPsiBuilder

/*
 * PostfixExpr ::= InfixExpr [id [nl]]
 */
object PostfixExpr extends ParsingRule {

  override def parse(implicit builder: ScalaPsiBuilder): Boolean = {
    val postfixMarker = builder.mark()
    if (!InfixExpr()) {
      postfixMarker.drop()
      return false
    }
    builder.getTokenType match {
      case ScalaTokenTypes.tIDENTIFIER if
          builder.features.`Scala 3 vararg splice syntax` &&
          builder.getTokenText == "*" &&
          builder.lookAhead(1) == ScalaTokenTypes.tRPARENTHESIS =>
        // Seq(a, ax*)
        postfixMarker.drop()
      case ScalaTokenTypes.tIDENTIFIER if !builder.newlineBeforeCurrentToken =>
        val refMarker = builder.mark()
        builder.advanceLexer() //Ate id
        refMarker.done(ScalaElementType.REFERENCE_EXPRESSION)
        /*builder.getTokenType match {
          case ScalaTokenTypes.tLINE_TERMINATOR => {
            if (LineTerminator(builder.getTokenText)) builder.advanceLexer
          }
          case _ => {}
        }*/
        postfixMarker.done(ScalaElementType.POSTFIX_EXPR)
      case _ =>
        postfixMarker.drop()
    }
    true
  }
}