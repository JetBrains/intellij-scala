package org.jetbrains.plugins.scala.codeInsight.intention.format

import org.jetbrains.plugins.scala.format.{FormattedStringFormatter, StringConcatenationParser}

/**
 * Pavel Fatin
 */

class ConvertStringConcatenationToFormattedString extends AbstractFormatConversionIntention(
  "Convert to formatted string", StringConcatenationParser, FormattedStringFormatter, eager = true) {
}
