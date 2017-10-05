package org.jetbrains.plugins.dotty.lang.parser.parsing.patterns

import org.jetbrains.plugins.dotty.lang.parser.parsing.expressions.Literal

/**
  * @author adkozlov
  */
object SimplePattern extends org.jetbrains.plugins.scala.lang.parser.parsing.patterns.SimplePattern {
  override protected def literal = Literal
  override protected def interpolationPattern = InterpolationPattern
  override protected def pattern = Pattern
  override protected def patterns = Patterns
}
