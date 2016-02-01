package org.jetbrains.plugins.scala
package lang.parser.parsing.patterns

import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.parser.parsing.CommonUtils
import org.jetbrains.plugins.scala.lang.parser.parsing.builder.ScalaPsiBuilder

/**
 * @author kfeodorov
 * @since 01.03.14.
 */
object InterpolationPattern extends InterpolationPattern {
  override protected val commonUtils = CommonUtils
}

trait InterpolationPattern {
  protected val commonUtils: CommonUtils

  def parse(builder: ScalaPsiBuilder): Boolean =
    builder.getTokenType match {
      case ScalaTokenTypes.tINTERPOLATED_STRING_ID =>
        commonUtils.parseInterpolatedString(builder, isPattern = true)
        true
      case _ => false
    }
}
