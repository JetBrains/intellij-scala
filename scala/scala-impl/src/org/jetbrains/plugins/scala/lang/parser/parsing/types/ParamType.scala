package org.jetbrains.plugins.scala
package lang
package parser
package parsing
package types

import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.parser.parsing.builder.ScalaPsiBuilder

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

  override def parse(implicit builder: ScalaPsiBuilder): Boolean =
    builder.build(ScalaElementType.PARAM_TYPE) {
      parseWithoutScParamTypeCreation(builder)
    }

  def parseWithoutScParamTypeCreation(implicit builder: ScalaPsiBuilder): Boolean = {
    val isByName = builder.getTokenType == ScalaTokenTypes.tFUNTYPE
    if (isByName) {
      builder.advanceLexer()
    }
    val allowStar = !isByName || builder.isScala3
    val parsedType = `type`(star = allowStar)

    if (parsedType && allowStar) {
      builder.getTokenText match {
        case "*" => builder.advanceLexer() // Ate '*'
        case _ => /* nothing needs to be done */
      }
    }
    parsedType
  }
}