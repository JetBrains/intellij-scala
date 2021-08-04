package org.jetbrains.plugins.scala.format

import com.intellij.psi.PsiElement
import org.jetbrains.plugins.scala.lang.psi.api.base.literals.ScStringLiteral

object AnyStringParser extends StringParser {

  override def parse(element: PsiElement): Option[Seq[StringPart]] = element match {
    case WithStrippedMargin.StripMarginCall(_, lit, _) =>
      StripMarginParser.parse(lit)
    case string: ScStringLiteral =>
      ScStringLiteralParser.parse(string, checkStripMargin = true)
    case _ =>
      val res0 = StringConcatenationParser.parse(element)
      val res1 = res0.orElse(FormattedStringParser.parse(element))
      res1
  }
}
