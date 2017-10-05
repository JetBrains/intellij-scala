package org.jetbrains.plugins.scala.injection

import com.intellij.openapi.util.TextRange
import com.intellij.openapi.util.text.StringUtil
import com.intellij.psi.AbstractElementManipulator
import com.intellij.util.IncorrectOperationException
import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.psi.api.base.{ScInterpolatedStringLiteral, ScLiteral}
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementFactory.createExpressionFromText

/**
  * User: Dmitry.Naydanov
  * Date: 05.11.15.
  */
class ScalaInjectedStringLiteralManipulator extends AbstractElementManipulator[ScLiteral] {
  override def handleContentChange(expr: ScLiteral, range: TextRange, newContent: String): ScLiteral = {
    val oldText = expr.getText
    val contentString = expr.getFirstChild.getNode.getElementType match {
      case ScalaTokenTypes.tMULTILINE_STRING => newContent
      case _ => StringUtil escapeStringCharacters newContent
    }
    val newText = oldText.substring(0, range.getStartOffset) + contentString + oldText.substring(range.getEndOffset)

    implicit val projectContext = expr.projectContext
    expr match {
      case inter: ScInterpolatedStringLiteral =>
        val quotes = if (inter.isMultiLineString) "\"\"\"" else "\""

        inter.reference.map { ref =>
          createExpressionFromText(s"${ref.getText}$quotes$newContent$quotes")
        } match {
          case Some(l: ScLiteral) => 
            expr.replace(l)
            l
          case _ => throw new IncorrectOperationException("cannot handle content change")
        }
      case str if str.isString =>
        val newExpr = createExpressionFromText(newText)

        val firstChild = str.getFirstChild
        val newElement = newExpr.getFirstChild

        assert(newElement != null)
        firstChild.replace(newElement)

        str
      case _ => throw new IncorrectOperationException("cannot handle content change")
    }
  }

  override def getRangeInElement(element: ScLiteral): TextRange = {
    if (element.isString) element match {
      case interp: ScInterpolatedStringLiteral =>
        val prefixLength = interp.reference match {
          case Some(ref) => ref.getText.length
          case _ => 0
        }
        getLiteralRange(element.getText.substring(prefixLength)).shiftRight(prefixLength)
      case _ =>
        getLiteralRange(element.getText)
    } else TextRange.from(0, element.getTextLength)
  }

  private def getLiteralRange(text: String): TextRange = {
    val tripleQuote = "\"\"\""
    
    if (text.length >= 6 && text.startsWith(tripleQuote) && text.endsWith(tripleQuote)) 
      new TextRange(3, text.length - 3) else new TextRange(1, Math.max(1, text.length - 1))
  }
}
