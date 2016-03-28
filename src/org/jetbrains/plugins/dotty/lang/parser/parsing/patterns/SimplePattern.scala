package org.jetbrains.plugins.dotty.lang.parser.parsing.patterns

import org.jetbrains.plugins.dotty.lang.parser.parsing.expressions.Literal

/**
  * @author adkozlov
  */
object SimplePattern extends org.jetbrains.plugins.scala.lang.parser.parsing.patterns.SimplePattern {
  override protected val literal = Literal
  override protected val interpolationPattern = InterpolationPattern
  override protected val pattern = Pattern
  override protected val patterns = Patterns
}
