package org.jetbrains.plugins.scala.injection

import com.intellij.openapi.util.TextRange
import com.intellij.openapi.util.text.StringUtil
import com.intellij.psi.AbstractElementManipulator
import org.jetbrains.plugins.scala.lang.psi.api.base._
import org.jetbrains.plugins.scala.lang.psi.api.base.literals.ScStringLiteral
import org.jetbrains.plugins.scala.lang.psi.impl._

final class ScalaInjectedStringLiteralManipulator extends AbstractElementManipulator[ScStringLiteral] {

  override def handleContentChange(
    literal: ScStringLiteral,
    range: TextRange,
    newContent: String
  ): ScStringLiteral = {
    val text = literal.getText

    val needEscape = literal match {
      case s: ScInterpolatedStringLiteral => s.kind != ScInterpolatedStringLiteral.Raw
      case _                              => !literal.isMultiLineString
    }

    val before = text.substring(0, range.getStartOffset)
    val after = text.substring(range.getEndOffset)
    val newContentEscaped = if (needEscape) StringUtil.escapeStringCharacters(newContent) else newContent
    val newText = before + newContentEscaped + after

    replaceWith(literal, newText)
  }

  override def getRangeInElement(element: ScStringLiteral): TextRange =
    if (element.isString)
      element.contentRange.shiftLeft(element.getTextRange.getStartOffset)
    else
      super.getRangeInElement(element)

  private def replaceWith(literal: ScStringLiteral, newText: String): ScStringLiteral = {
    val expression = ScalaPsiElementFactory.createExpressionFromText(newText, literal)(literal)
    (expression, literal) match {
      case (newInterpolatedLiteral: ScInterpolatedStringLiteral, _: ScInterpolatedStringLiteral) =>
        literal.replace(newInterpolatedLiteral).asInstanceOf[ScStringLiteral]
      case (newLiteral: ScStringLiteral, _) =>
        newLiteral.getFirstChild match {
          case null => null
          case newLeaf =>
            literal.getFirstChild.replace(newLeaf)
            literal
        }
      case _ => null
    }
  }

}

