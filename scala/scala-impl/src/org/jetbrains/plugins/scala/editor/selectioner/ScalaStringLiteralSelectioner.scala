package org.jetbrains.plugins.scala.editor.selectioner

import java.{util => ju}

import com.intellij.codeInsight.editorActions.{ExtendWordSelectionHandlerBase, SelectWordUtil}
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import org.jetbrains.plugins.scala.extensions.{PsiElementExt, TextRangeExt}
import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.parser.{ScCodeBlockElementType, ScalaElementType}
import org.jetbrains.plugins.scala.lang.psi.api.base.ScInterpolatedStringLiteral
import org.jetbrains.plugins.scala.lang.psi.api.base.literals.ScStringLiteral
import org.jetbrains.plugins.scala.util.MultilineStringUtil
import org.jetbrains.plugins.scala.util.MultilineStringUtil.MultilineQuotes

import scala.jdk.CollectionConverters._

class ScalaStringLiteralSelectioner extends ExtendWordSelectionHandlerBase {

  override def canSelect(e: PsiElement): Boolean =
    isStringLiteral(e) || isStringLiteral(e.getParent) || isInterpolatedStringLiteral(e.getParent)

  private def isStringLiteral(e: PsiElement): Boolean = e match {
    case _: ScStringLiteral => true
    case _                  => false
  }

  private def isInterpolatedStringLiteral(e: PsiElement): Boolean = e match {
    case _: ScInterpolatedStringLiteral => true
    case _                              => false
  }

  override def select(e: PsiElement, editorText: CharSequence, cursorOffset: Int, editor: Editor): ju.List[TextRange] =
    if (isInterpolatedStringLiteral(e.getParent)) {
      selectInterpolated(e, editorText, cursorOffset, editor)
    } else {
      selectOrdinary(e, editorText, cursorOffset, editor)
    }

  private def selectOrdinary(e: PsiElement, editorText: CharSequence, cursorOffset: Int, editor: Editor): ju.List[TextRange] = {
    val ranges = super.select(e, editorText, cursorOffset, editor)
    val text = e.getText
    // TODO: redo? use utils
    if (text.startsWith(MultilineQuotes) && text.endsWith(MultilineQuotes) && text.length > 6) {
      val contentRange = e.getTextRange.shrink(3)
      ranges.add(contentRange)
    } else if (text.startsWith("\"") && text.endsWith("\"") && text.length > 2) {
      val contentRange = e.getTextRange.shrink(1)
      ranges.add(contentRange)
    }
    ranges
  }

  /**
   * Some magic inside this method is required due to current akward parsing of interpolated multiline string
   * See tests and play with string literals in `View PSI Structure`.
   */
  private def selectInterpolated(e: PsiElement, editorText: CharSequence, cursorOffset: Int, editor: Editor): ju.List[TextRange] = {
    val ranges = super.select(e, editorText, cursorOffset, editor).asScala
    val stringLiteral = e.getParent match {
      case l: ScInterpolatedStringLiteral => l
      case _ => return ranges.asJava
    }

    import ScalaTokenTypes._

    val contentRange = MultilineStringUtil.contentRange(stringLiteral)
    val rangesFixed = e.elementType match {
      case `tINTERPOLATED_MULTILINE_STRING` | `tINTERPOLATED_STRING` =>
        ranges.filter(_ != e.getTextRange)// remove ugly range from parser: s<START>"""content<END>"""
      case ScalaTokenTypes.tINTERPOLATED_STRING_INJECTION =>
        val range = e.getNextSibling.getTextRange
        val rangeWithDollar = range.shiftStart(-1)
        ranges.filter(_ != e.getTextRange) :+ range :+ rangeWithDollar // do not select single dollar sign
      case ScCodeBlockElementType.BlockExpression | ScalaElementType.REFERENCE_EXPRESSION =>
        val rangeWithDollar = e.getTextRange.shiftStart(-1)
        ranges :+ rangeWithDollar
      case `tINTERPOLATED_STRING_END` =>
        Seq(stringLiteral.getTextRange)
      case _ =>
        ranges
    }
    val result = new ju.ArrayList((contentRange +: rangesFixed).asJava)
    if (addDefaultWordSelection(e))
      SelectWordUtil.addWordOrLexemeSelection(editor.getSettings.isCamelWords, editor, cursorOffset, result)
    result
  }

  private def addDefaultWordSelection(e: PsiElement): Boolean = {
    import ScalaTokenTypes._
    e.elementType match {
      case `tINTERPOLATED_MULTILINE_STRING` | `tINTERPOLATED_STRING` => true
      case _                                                         => false
    }
  }
}
