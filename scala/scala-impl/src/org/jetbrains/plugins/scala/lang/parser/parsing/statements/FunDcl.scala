package org.jetbrains.plugins.scala
package lang
package parser
package parsing
package statements

import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.parser.parsing.builder.ScalaPsiBuilder
import org.jetbrains.plugins.scala.lang.parser.parsing.types.Type

/**
* @author Alexander Podkhalyuzin
* Date: 11.02.2008
*/

/*
 * FunDcl ::= FunSig [':' Type]
 */
object FunDcl extends FunDcl {
  override protected def funSig = FunSig
  override protected def `type` = Type
}

trait FunDcl {
  protected def funSig: FunSig
  protected def `type`: Type

  def parse(builder: ScalaPsiBuilder): Boolean = {
    //val returnMarker = builder.mark
    builder.getTokenType match {
      case ScalaTokenTypes.kDEF =>
        builder.advanceLexer //Ate def
      case _ =>
        //returnMarker.drop
        return false
    }
    if (!(funSig parse builder)) {
      //returnMarker.drop
      return false
    }
    builder.getTokenType match {
      case ScalaTokenTypes.tCOLON =>
        builder.advanceLexer //Ate :
        if (`type`.parse(builder)) {
          //returnMarker.drop
          return true
        }
        else {
          builder error ScalaBundle.message("wrong.type")
          //returnMarker.drop
          return true
        }
      case _ =>
        //returnMarker.drop
        return true
    }
  }
}