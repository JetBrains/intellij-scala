package org.jetbrains.plugins.scala
package lang
package parser
package parsing
package types

import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.parser.parsing.builder.ScalaPsiBuilder

/**
* @author Alexander Podkhalyuzin
* Date: 08.02.2008
*/

/*
 * ParamType ::= Type |
 *               '=>' Type |
 *               Type '*'
 */
object ParamType extends ParamType {
  override protected def `type`: Type = Type
}

trait ParamType {
  protected def `type`: Type

  def parse(builder: ScalaPsiBuilder): Boolean =
    builder.build(ScalaElementType.PARAM_TYPE) {
      parseInner
    }

  def parseInner(builder: ScalaPsiBuilder): Boolean = {
    builder.getTokenType match {
      case ScalaTokenTypes.tFUNTYPE =>
        builder.advanceLexer() //Ate '=>'
        `type`.parse(builder)
      case _ =>
        if (!`type`.parse(builder, star = true)) false
        else {
          builder.getTokenText match {
            case "*" => builder.advanceLexer() // Ate '*'
            case _ => /* nothing needs to be done */
          }
          true
        }
    }
  }
}