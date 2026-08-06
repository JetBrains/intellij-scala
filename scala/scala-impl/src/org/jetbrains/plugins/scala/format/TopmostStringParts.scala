package org.jetbrains.plugins.scala.format

import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.format
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScExpression

class TopmostStringParts(innerParser: StringParser) {
  final def unapply(expr: ScExpression): Option[Seq[format.StringPart]] = {
    def parentIsStringConcatenationOrFormat = expr.parent.exists {
      case format.StringConcatenationExpression(_, _) => true
      case p => format.FormattedStringParser.parse(p).isDefined
    }
    innerParser.parse(expr).filterNot(_ => parentIsStringConcatenationOrFormat)
  }
}

object AnyTopmostStringParts extends TopmostStringParts(AnyStringParser)