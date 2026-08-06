package org.jetbrains.plugins.scala.format

import org.jetbrains.plugins.scala.lang.psi.api.base.ScInterpolatedStringLiteral
import org.jetbrains.plugins.scala.lang.psi.api.base.literals.ScStringLiteral

object ScStringLiteralParser {

  def parse(literal: ScStringLiteral, checkStripMargin: Boolean): Option[Seq[StringPart]] =
    literal match {
      case _: ScInterpolatedStringLiteral =>
        InterpolatedStringParser.parse(literal, checkStripMargin)
      case _ =>
        val content = Option(literal.contentText)
        content.map { s0 =>
          val isRawContent = literal.isMultiLineString
          val s = ScalaStringUtils.unescapeStringCharacters(s0, isRaw = isRawContent, noUnicodeEscapesInRawStrings = literal.noUnicodeEscapesInRawStrings)
          Text(s) :: Nil
        }
    }
}
