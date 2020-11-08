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

trait ParamType extends ParsingRule {
  protected def `type`: Type

  override def apply()(implicit builder: ScalaPsiBuilder): Boolean =
    builder.build(ScalaElementType.PARAM_TYPE) {
      parseInner
    }

  def parseInner(builder: ScalaPsiBuilder): Boolean = {
    val isByName = builder.getTokenType == ScalaTokenTypes.tFUNTYPE
    if (isByName) {
      builder.advanceLexer()
    }
    val allowStar = !isByName || builder.isScala3
    val parsedType = `type`.parse(builder, star = allowStar)

    if (parsedType && allowStar) {
      builder.getTokenText match {
        case "*" => builder.advanceLexer() // Ate '*'
        case _ => /* nothing needs to be done */
      }
    }
    parsedType
  }
}