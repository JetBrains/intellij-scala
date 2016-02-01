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
object NameValuePair extends NameValuePair {
  override protected val prefixExpr = PrefixExpr
}

trait NameValuePair {
  protected val prefixExpr: PrefixExpr

  def parse(builder: ScalaPsiBuilder): Boolean = {
    val nameMarker = builder.mark
    builder.getTokenType match {
      case ScalaTokenTypes.kVAL =>
        builder.advanceLexer() //Ate val
      case _ =>
        nameMarker.drop()
        return false
    }
    builder.getTokenType match {
      case ScalaTokenTypes.tIDENTIFIER =>
        builder.advanceLexer() //Ate id
      case _ =>
        builder error ScalaBundle.message("identifier.expected")
        nameMarker.done(ScalaElementTypes.NAME_VALUE_PAIR)
        return true
    }
    builder.getTokenType match {
      case ScalaTokenTypes.tASSIGN =>
        builder.advanceLexer() //Ate =
      case _ =>
        builder error ScalaBundle.message("assign.expected")
    }
    if (!prefixExpr.parse(builder)) {
      builder error ScalaBundle.message("wrong.expression")
    }
    nameMarker.done(ScalaElementTypes.NAME_VALUE_PAIR)
    true
  }
}