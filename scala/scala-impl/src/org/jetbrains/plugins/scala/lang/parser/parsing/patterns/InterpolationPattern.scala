package org.jetbrains.plugins.scala
package lang.parser.parsing.patterns

import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.parser.parsing.builder.ScalaPsiBuilder
import org.jetbrains.plugins.scala.lang.parser.parsing.{CommonUtils, ParsingRule}

object InterpolationPattern extends ParsingRule {

  override def parse(implicit builder: ScalaPsiBuilder): Boolean =
    builder.getTokenType match {
      case ScalaTokenTypes.tINTERPOLATED_STRING_ID =>
        CommonUtils.parseInterpolatedString(isPattern = true)
        true
      case _ => false
    }
}
