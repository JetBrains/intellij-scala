package org.jetbrains.plugins.scala
package lang
package parser
package parsing
package statements

import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.parser.parsing.builder.ScalaPsiBuilder
import org.jetbrains.plugins.scala.lang.parser.parsing.types.Type

/*
 * FunDcl ::= FunSig [':' Type]
 */
object FunDcl extends ParsingRule {

  override def parse(implicit builder: ScalaPsiBuilder): Boolean = {
    //val returnMarker = builder.mark()
    builder.getTokenType match {
      case ScalaTokenTypes.kDEF =>
        builder.advanceLexer() //Ate def
      case _ =>
        //returnMarker.drop
        return false
    }
    if (!FunSig()) {
      //returnMarker.drop
      return false
    }
    builder.getTokenType match {
      case ScalaTokenTypes.tCOLON =>
        builder.advanceLexer() //Ate :
        if (Type()) {
          //returnMarker.drop
          true
        }
        else {
          builder error ScalaBundle.message("wrong.type")
          //returnMarker.drop
          true
        }
      case _ =>
        //returnMarker.drop
        true
    }
  }
}