package org.jetbrains.plugins.scala
package lang
package parser
package parsing
package statements

import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.parser.parsing.base.Ids
import org.jetbrains.plugins.scala.lang.parser.parsing.builder.ScalaPsiBuilder
import org.jetbrains.plugins.scala.lang.parser.parsing.types.Type

/**
* @author Alexander Podkhalyuzin
* Date: 11.02.2008
*/

/*
 * ValDcl ::= ids ':' Type
 */
object ValDcl extends ValDcl {
  override protected val `type` = Type
}

trait ValDcl {
  protected val `type`: Type

  def parse(builder: ScalaPsiBuilder): Boolean = {
    val returnMarker = builder.mark
    //Look for val
    builder.getTokenType match {
      case ScalaTokenTypes.kVAL => builder.advanceLexer() //Ate val
      case _ =>
        returnMarker.rollbackTo
        return false
    }
    //Look for identifier
    builder.getTokenType match {
      case ScalaTokenTypes.tIDENTIFIER =>
        Ids parse builder
        //Look for :
        builder.getTokenType match {
          case ScalaTokenTypes.tCOLON => {
            builder.advanceLexer //Ate :
            if (`type`.parse(builder)) {
              returnMarker.drop
            }
            else {
              builder error ErrMsg("wrong.type")
              returnMarker.drop
            }
          }
          case _ => {
            builder error ErrMsg("wrong.val.declaration")
            returnMarker.drop
          }
        }

        builder.getTokenType match {
          case ScalaTokenTypes.tASSIGN => {
            builder.advanceLexer
            builder.error("Expected expression")
            return true
          }
          case _ => {
            return true
          }
        }
      case _ =>
        builder error ErrMsg("identifier.expected")
        returnMarker.drop
        return false
    }
  }
}