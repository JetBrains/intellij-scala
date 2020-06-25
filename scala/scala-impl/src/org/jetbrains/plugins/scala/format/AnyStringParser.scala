package org.jetbrains.plugins.scala.format
import com.intellij.psi.PsiElement
import org.jetbrains.plugins.scala.lang.psi.api.base.ScInterpolatedStringLiteral
import org.jetbrains.plugins.scala.lang.psi.api.base.literals.ScStringLiteral

object AnyStringParser extends StringParser {
  override def parse(element: PsiElement): Option[Seq[StringPart]] = element match {
    case WithStrippedMargin.StripMarginCall(_, lit, _) => StripMarginParser.parse(lit)
    case interpolated: ScInterpolatedStringLiteral => InterpolatedStringParser.parse(interpolated)
    case ScStringLiteral(string) => Some(Seq(Text(string)))
    case _ => StringConcatenationParser.parse(element) orElse FormattedStringParser.parse(element)
  }
}
