package org.jetbrains.plugins.scala
package lang
package parser
package parsing
package statements

import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.parser.parsing.base.Ids
import org.jetbrains.plugins.scala.lang.parser.parsing.builder.ScalaPsiBuilder
import org.jetbrains.plugins.scala.lang.parser.parsing.types.Type

/*
 * VarDcl ::= ids ':' Type
 */
object VarDcl extends ParsingRule {
  override def parse(implicit builder: ScalaPsiBuilder): Boolean = {
    val returnMarker = builder.mark()
    //Look for val
    builder.getTokenType match {
      case ScalaTokenTypes.kVAR => builder.advanceLexer() //Ate var
      case _ =>
        returnMarker.rollbackTo()
        return false
    }
    //Look for identifier
    builder.getTokenType match {
      case ScalaTokenTypes.tIDENTIFIER =>
        Ids()
        //Look for :
        builder.getTokenType match {
          case ScalaTokenTypes.tCOLON =>
            builder.advanceLexer() //Ate :
            if (Type()) {
              returnMarker.drop()
            }
            else {
              builder error ScalaBundle.message("wrong.type")
              returnMarker.drop()
            }
          case _ =>
            builder error ScalaBundle.message("wrong.var.declaration")
            returnMarker.drop()
        }

        builder.getTokenType match {
          case ScalaTokenTypes.tASSIGN =>
            builder.advanceLexer()
            builder.error(ScalaBundle.message("expression.expected"))
          case _ =>
        }
        true
      case _ =>
        builder error ScalaBundle.message("identifier.expected")
        returnMarker.drop()
        false
    }
  }
}