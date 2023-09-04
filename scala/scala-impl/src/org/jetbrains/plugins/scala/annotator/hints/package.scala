package org.jetbrains.plugins.scala.annotator

import com.intellij.openapi.editor.colors.{CodeInsightColors, EditorColors, EditorColorsScheme}
import com.intellij.openapi.editor.markup.TextAttributes
import com.intellij.util.ui.StartupUiUtil

import java.awt.Color

// TODO Use built-in "advanced" hint API when it will be available in IDEA, SCL-14502
package object hints {
  implicit class TextAttributesExt(private val v: TextAttributes) extends AnyVal {
    def +(attributes: TextAttributes): TextAttributes = {
      val result = v.clone()
      Option(attributes.getForegroundColor).foreach(result.setForegroundColor)
      Option(attributes.getBackgroundColor).foreach(result.setBackgroundColor)
      Option(attributes.getFontType).foreach(result.setFontType)
      Option(attributes.getEffectType).foreach(result.setEffectType)
      Option(attributes.getEffectColor).foreach(result.setEffectColor)
      result
    }

    def ++(attributes: Iterable[TextAttributes]): TextAttributes =
      attributes.foldLeft(v)(_ + _)
  }

  implicit class SeqTextExt(private val parts: Seq[Text]) extends AnyVal {

    def withAttributes(attr: TextAttributes): Seq[Text] = parts.map(_.withAttributes(attr))

    //nested text may have more specific tooltip
    def withErrorTooltipIfEmpty(tooltip: ErrorTooltip): Seq[Text] = parts.map { p =>
      if (p.errorTooltip.isEmpty) p.withErrorTooltip(tooltip) else p
    }

    def withErrorTooltipIfEmpty(tooltip: Option[ErrorTooltip]): Seq[Text] = tooltip.map(parts.withErrorTooltipIfEmpty).getOrElse(parts)
  }

  val foldedString: String = "..."

  def foldedAttributes(error: Boolean)(implicit scheme: EditorColorsScheme): Option[TextAttributes] = {
    val plainFolded =
      Option(scheme.getAttributes(EditorColors.FOLDED_TEXT_ATTRIBUTES))
        .map(adjusted)

    if (error) plainFolded.map(_ + errorAttributes)
    else plainFolded
  }

  def errorAttributes(implicit scheme: EditorColorsScheme): TextAttributes =
    scheme.getAttributes(CodeInsightColors.ERRORS_ATTRIBUTES)

  def likeWrongReference(implicit scheme: EditorColorsScheme): Option[TextAttributes] =
    Option(scheme.getAttributes(CodeInsightColors.WRONG_REFERENCES_ATTRIBUTES))

  // Add custom colors for folding inside inlay hints (SCL-13996)?
  private def adjusted(attributes: TextAttributes): TextAttributes = {
    def average(c1: Color, c2: Color) = {
      val r = (c1.getRed + c2.getRed) / 2
      val g = (c1.getGreen + c2.getGreen) / 2
      val b = (c1.getBlue + c2.getBlue) / 2
      val alpha = c1.getAlpha
      new Color(r, g, b, alpha)
    }

    val result = attributes.clone()
    if (StartupUiUtil.isDarkTheme && result.getBackgroundColor != null) {
      val notSoBright = result.getBackgroundColor.brighter
      val tooBright = notSoBright.brighter
      result.setBackgroundColor(average(notSoBright, tooBright))
    }
    result
  }

  def onlyErrorStripeAttributes(implicit scheme: EditorColorsScheme): TextAttributes = {
    val errorStripeColor = scheme.getAttributes(CodeInsightColors.ERRORS_ATTRIBUTES).getErrorStripeColor
    val attributes = new TextAttributes()
    attributes.setEffectType(null)
    attributes.setErrorStripeColor(errorStripeColor)
    attributes
  }
}
