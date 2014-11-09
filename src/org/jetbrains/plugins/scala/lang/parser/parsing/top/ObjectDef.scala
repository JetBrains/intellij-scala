package org.jetbrains.plugins.scala
package lang
package parser
package parsing
package top

import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.parser.parsing.builder.ScalaPsiBuilder

/**
* @author Alexander Podkhalyuzin
* Date: 06.02.2008
*/

/*
 *  ObjectDef ::= id ClassTemplateOpt
 */

object ObjectDef {
  def parse(builder: ScalaPsiBuilder): Boolean = {
    builder.getTokenType match {
      case ScalaTokenTypes.tIDENTIFIER => builder.advanceLexer() //Ate identifier
      case _ => {
        builder error ScalaBundle.message("identifier.expected")
        return false
      }
    }
    //parse extends block
    ClassTemplateOpt parse builder
    return true
  }
}