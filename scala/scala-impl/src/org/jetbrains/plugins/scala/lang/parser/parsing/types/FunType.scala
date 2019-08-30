package org.jetbrains.plugins.scala.lang.parser.parsing.types

import org.jetbrains.plugins.scala.lang.parser.parsing.ParsingRule
import org.jetbrains.plugins.scala.lang.parser.parsing.builder.ScalaPsiBuilder

/**
 * [[FunType]] ::= [[MonoFunType]] | [[PolyFunType]]
 */
object FunType extends ParsingRule {
  override def parse()(implicit builder: ScalaPsiBuilder): Boolean = {
    val marker = builder.mark()

    if (MonoFunType.parse() || PolyFunType.parse()) {
      marker.drop()
      true
    } else {
      marker.rollbackTo()
      false
    }
  }
}
