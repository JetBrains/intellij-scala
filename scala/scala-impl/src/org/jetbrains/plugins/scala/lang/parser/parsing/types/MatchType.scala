package org.jetbrains.plugins.scala.lang.parser.parsing.types

import org.jetbrains.plugins.scala.lang.parser.ScalaElementType
import org.jetbrains.plugins.scala.lang.parser.parsing.ParsingRule
import org.jetbrains.plugins.scala.lang.parser.parsing.builder.ScalaPsiBuilder

/**
 * [[MatchType]] ::= [[InfixType]] [[MatchTypeSuffix]]
 */
object MatchType extends ParsingRule {
  override def parse()(implicit builder: ScalaPsiBuilder): Boolean = {
    val marker = builder.mark()

    if (!InfixType.parse(builder)) {
      marker.rollbackTo()
      false
    } else {
      val parsedSuffix = MatchTypeSuffix.parse()

      if (!parsedSuffix) {
        marker.rollbackTo()
        false
      } else {
        marker.done(ScalaElementType.MATCH_TYPE)
        true
      }
    }
  }
}
