package org.jetbrains.plugins.scala.codeInsight.intention.format

import org.jetbrains.plugins.scala.format.{InterpolatedStringParser, StringConcatenationFormatter}

/**
 * Pavel Fatin
 */

class ConvertInterpolatedStringToStringConcatenation extends AbstractFormatConversionIntention(
  "Convert to string concatenation", InterpolatedStringParser, StringConcatenationFormatter) {
}
