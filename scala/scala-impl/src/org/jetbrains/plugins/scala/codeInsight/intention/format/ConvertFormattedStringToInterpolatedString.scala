package org.jetbrains.plugins.scala
package codeInsight.intention.format

import org.jetbrains.plugins.scala.format.{FormattedStringParser, InterpolatedStringFormatter}

/**
 * Pavel Fatin
 */

class ConvertFormattedStringToInterpolatedString extends AbstractFormatConversionIntention(
  "Convert to interpolated string", FormattedStringParser, InterpolatedStringFormatter) {
}
