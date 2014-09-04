package org.jetbrains.plugins.scala.codeInsight.intention.format

import org.jetbrains.plugins.scala.format.{FormattedStringParser, StringConcatenationFormatter}

/**
 * Pavel Fatin
 */

class ConvertFormattedStringToStringConcatenation extends AbstractFormatConversionIntention(
  "Convert to string concatenation", FormattedStringParser, StringConcatenationFormatter) {
}
