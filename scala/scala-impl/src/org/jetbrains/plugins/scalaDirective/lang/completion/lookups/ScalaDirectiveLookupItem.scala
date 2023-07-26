package org.jetbrains.plugins.scalaDirective.lang.completion.lookups

import com.intellij.codeInsight.completion.{InsertHandler, InsertionContext}
import com.intellij.codeInsight.lookup.{LookupElement, LookupElementBuilder}
import com.intellij.openapi.editor.{Document, Editor}
import com.intellij.util.ui.EmptyIcon
import org.jetbrains.plugins.scalaDirective.lang.completion.UsingDirective

object ScalaDirectiveLookupItem {
  def apply(text: String): LookupElement =
    LookupElementBuilder.create(text)
      .withBoldness(true)
      .withIcon(EmptyIcon.create(16, 16))
      .withInsertHandler(new ScalaDirectiveInsertHandler(text))

  final class ScalaDirectiveInsertHandler(val text: String) extends InsertHandler[LookupElement] {

    import ScalaDirectiveInsertHandler._

    override def handleInsert(context: InsertionContext, item: LookupElement): Unit = {
      implicit val (document, editor) = (context.getDocument, context.getEditor)
      val startOffset = context.getStartOffset
      val endOffset = startOffset + text.length

      text match {
        case UsingDirective =>
          insertSpaceAfterIfNeeded(endOffset)
          moveCaret(endOffset + 1)
          insertSpaceBeforeIfNeeded(startOffset)
        case _ =>
      }
    }
  }

  object ScalaDirectiveInsertHandler {
    private def insertSpaceAfterIfNeeded(offset: Int)(implicit document: Document): Unit =
      if (!(offset < document.getTextLength &&
        document.getImmutableCharSequence.charAt(offset) == ' ')) {
        document.insertString(offset, " ")
      }

    private def insertSpaceBeforeIfNeeded(offset: Int)(implicit document: Document): Unit =
      if (offset > 0 && document.getImmutableCharSequence.charAt(offset - 1) != ' ') {
        document.insertString(offset, " ")
      }

    private def moveCaret(offset: Int)(implicit editor: Editor): Unit =
      editor.getCaretModel.moveToOffset(offset)
  }
}
