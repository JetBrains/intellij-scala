package org.jetbrains.plugins.scala.codeInsight.intention.format

import org.jetbrains.plugins.scala.format.{InterpolatedStringFormatter, FormattedStringParser}

/**
 * Pavel Fatin
 */

class ConvertFormattedStringToInterpolatedString extends AbstractFormatConversionIntention(
  "Convert to interpolated string", FormattedStringParser, InterpolatedStringFormatter) {
}
