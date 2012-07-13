package org.jetbrains.plugins.scala.codeInsight.intention.format

import org.jetbrains.plugins.scala.format.{StringConcatenationFormatter, InterpolatedStringParser}

/**
 * Pavel Fatin
 */

class ConvertInterpolatedStringToStringConcatenation extends AbstractFormatConversionIntention(
  "Convert to string concatenation", InterpolatedStringParser, StringConcatenationFormatter) {
}
