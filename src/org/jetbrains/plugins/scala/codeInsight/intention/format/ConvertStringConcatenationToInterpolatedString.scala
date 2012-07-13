package org.jetbrains.plugins.scala.codeInsight.intention.format

import org.jetbrains.plugins.scala.format.{InterpolatedStringFormatter, StringConcatenationParser}

/**
 * Pavel Fatin
 */

class ConvertStringConcatenationToInterpolatedString extends AbstractFormatConversionIntention(
  "Convert to interpolated string", StringConcatenationParser, InterpolatedStringFormatter, eager = true) {
}
