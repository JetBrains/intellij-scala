package org.jetbrains.plugins.scala.format

import org.jetbrains.plugins.scala.lang.psi.api.base.literals.ScStringLiteral

object StripMarginParser extends StringParserLike[ScStringLiteral] {

  override def parse(literal: ScStringLiteral): Option[Seq[StringPart]] = literal match {
    case WithStrippedMargin(_, marginChar) =>
      val res = ScStringLiteralParser.parse(literal, checkStripMargin = false)
      res.map(_.map(stripMargin(_, marginChar)))
    case _ =>
      None
  }

  private def stripMargin(part: StringPart, marginChar: Char): StringPart = part match {
    case Text(text) => Text(text.stripMargin(marginChar))
    case part       => part
  }
}
