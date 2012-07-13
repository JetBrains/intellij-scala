package org.jetbrains.plugins.scala.codeInsight.intention.format

import org.jetbrains.plugins.scala.format.{StringConcatenationFormatter, FormattedStringParser}

/**
 * Pavel Fatin
 */

class ConvertFormattedStringToStringConcatenation extends AbstractFormatConversionIntention(
  "Convert to string concatenation", FormattedStringParser, StringConcatenationFormatter) {
}
