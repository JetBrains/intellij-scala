package org.jetbrains.plugins.scala.editor.selectioner

import com.intellij.codeInsight.editorActions.ExtendWordSelectionHandlerBase
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import com.intellij.psi.tree.IElementType
import org.jetbrains.plugins.scala.editor.selectioner.ScalaAttributeValueSelectioner._
import org.jetbrains.plugins.scala.extensions.PsiElementExt
import org.jetbrains.plugins.scala.lang.lexer.ScalaXmlTokenTypes._

import java.util

class ScalaAttributeValueSelectioner extends ExtendWordSelectionHandlerBase {
  override def canSelect(e: PsiElement): Boolean = isPartOfAttributeValue(e)

  override def select(e: PsiElement, editorText: CharSequence, cursorOffset: Int, editor: Editor): util.List[TextRange] = {
    val result = super.select(e, editorText, cursorOffset, editor)

    val previous = Seq(e) ++ e.prevSiblings
    val start = previous.takeWhile(isPartOfAttributeValue).last

    val next = Seq(e) ++ e.nextSiblings
    val end = next.takeWhile(isPartOfAttributeValue).last

    if (start != end) {
      result.add(new TextRange(start.getTextRange.getStartOffset, end.getTextRange.getEndOffset))
    }

    result
  }
}

private object ScalaAttributeValueSelectioner {
  private val ValueTokenTypes: Set[IElementType] = Set(
    XML_ATTRIBUTE_VALUE_START_DELIMITER,
    XML_ATTRIBUTE_VALUE_TOKEN,
    XML_ATTRIBUTE_VALUE_END_DELIMITER)

  private def isPartOfAttributeValue(e: PsiElement): Boolean =
    ValueTokenTypes.contains(e.getNode.getElementType)
}