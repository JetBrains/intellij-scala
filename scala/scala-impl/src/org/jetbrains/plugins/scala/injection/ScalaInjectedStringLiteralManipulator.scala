package org.jetbrains.plugins.scala
package injection

import com.intellij.openapi.util.TextRange
import com.intellij.openapi.util.text.StringUtil
import com.intellij.psi.AbstractElementManipulator
import com.intellij.util.IncorrectOperationException
import org.jetbrains.plugins.scala.lang.psi.api.base._
import org.jetbrains.plugins.scala.lang.psi.impl._

final class ScalaInjectedStringLiteralManipulator extends AbstractElementManipulator[ScLiteral] {

  override def handleContentChange(literal: ScLiteral,
                                   range: TextRange,
                                   newContent: String): ScLiteral = {
    val text = literal.getText

    val needEscape = !literal.isInstanceOf[ScInterpolatedStringLiteral] && !literal.isMultiLineString
    val newText = text.substring(0, range.getStartOffset) +
      (if (needEscape) StringUtil.escapeStringCharacters(newContent) else newContent) +
      text.substring(range.getEndOffset)

    replaceWith(literal, newText) match {
      case targetLiteral: ScLiteral => targetLiteral
      case _ => throw new IncorrectOperationException(s"Cannot handle content change on `$text`: `$newContent`")
    }
  }

  override def getRangeInElement(element: ScLiteral): TextRange =
    if (element.isString) element.contentRange.shiftLeft(element.getTextRange.getStartOffset)
    else super.getRangeInElement(element)

  private def replaceWith(literal: ScLiteral, newText: String) =
    (ScalaPsiElementFactory.createExpressionFromText(newText)(literal), literal) match {
      case (newInterpolatedLiteral: ScInterpolatedStringLiteral, _: ScInterpolatedStringLiteral) =>
        literal.replace(newInterpolatedLiteral)
      case (newLiteral: ScLiteral, _) =>
        newLiteral.getFirstChild match {
          case null => null
          case newLeaf =>
            literal.getFirstChild.replace(newLeaf)
            literal
        }
      case _ => null
    }

}

