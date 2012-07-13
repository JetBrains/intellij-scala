package org.jetbrains.plugins.scala.codeInsight.intention.format

import org.jetbrains.plugins.scala.format.{FormattedStringFormatter, InterpolatedStringParser}

/**
 * Pavel Fatin
 */

class ConvertInterpolatedStringToFormattedString extends AbstractFormatConversionIntention(
  "Convert to formatted string", InterpolatedStringParser, FormattedStringFormatter) {
}
