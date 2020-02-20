package org.jetbrains.plugins.scala
package lang
package parser
package parsing
package statements

import org.jetbrains.plugins.scala.ScalaBundle
import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.parser.parsing.base.Ids
import org.jetbrains.plugins.scala.lang.parser.parsing.builder.ScalaPsiBuilder
import org.jetbrains.plugins.scala.lang.parser.parsing.types.Type

/**
* @author Alexander Podkhalyuzin
* Date: 06.02.2008
*/

/*
 *  ValDef ::= PatDef |
 *             ids ':' Type '=' '_'
 */
object VarDef {

  def parse(builder: ScalaPsiBuilder): Boolean = {
    if (PatDef.parse(builder)) {
      return true
    }

    // Parsing specifig wildcard definition
    val valDefMarker = builder.mark
    builder.getTokenType match {
      case ScalaTokenTypes.tIDENTIFIER =>
        Ids parse builder
        var hasTypeDcl = false

        if (ScalaTokenTypes.tCOLON.equals(builder.getTokenType)) {
          builder.checkedAdvanceLexer()
          if (!Type.parse(builder)) {
            builder error ScalaBundle.message("type.declaration.expected")
          }
          hasTypeDcl = true
        }
        else {
          valDefMarker.rollbackTo()
          return false
        }
        if (!ScalaTokenTypes.tASSIGN.equals(builder.getTokenType)) {
          valDefMarker.rollbackTo()
          false
        } else {
          builder.checkedAdvanceLexer()
          builder.getTokenType match {
            case ScalaTokenTypes.tUNDER => builder.advanceLexer()
            //Ate _
            case _ =>
              valDefMarker.rollbackTo()
              return false
          }
          valDefMarker.drop()
          true
        }
      case _ =>
        valDefMarker.drop()
        false
    }
  }
}