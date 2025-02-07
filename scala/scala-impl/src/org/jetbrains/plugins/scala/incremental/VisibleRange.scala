package org.jetbrains.plugins.scala
package incremental

import com.intellij.openapi.editor.{Editor, EditorFactory, LogicalPosition}
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.util.{Key, TextRange}
import com.intellij.psi.{PsiDocumentManager, PsiElement}

import java.awt.Point

private object VisibleRange {
  private val VISIBLE_RANGE_KEY = Key.create[TextRange]("editor_visible_range")

  private def lookaround: Int = Registry.intValue("scala.incremental.highlighting.lookaround")

  def isVisible(e: PsiElement): Boolean = {
    val elementRange = e.getTextRange

    editorsFor(e).exists { editor =>
      val visibleRange = editor.getUserData(VISIBLE_RANGE_KEY)
      visibleRange == null || elementRange.intersects(visibleRange) && !isFolded(editor, elementRange)
    }
  }

  private def isFolded(editor: Editor, range: TextRange): Boolean = {
    val foldingModel = editor.getFoldingModel
    val region1 = foldingModel.getCollapsedRegionAtOffset(range.getStartOffset)
    if (region1 == null) return false
    val region2 = foldingModel.getCollapsedRegionAtOffset(range.getEndOffset - 1)
    region1 == region2
  }

  def editorsFor(e: PsiElement): Seq[Editor] = {
    val psiFile = e.getContainingFile
    if (psiFile == null) return Seq.empty

    val document = PsiDocumentManager.getInstance(e.getProject).getDocument(psiFile)
    if (document == null) return Seq.empty

    EditorFactory.getInstance.getEditors(document).toSeq
  }

  def saveIn(editor: Editor): Unit = {
    editor.putUserData(VisibleRange.VISIBLE_RANGE_KEY, visibleRangeIn(editor, lookaround))
  }

  def in(editor: Editor): TextRange =
    editor.getUserData(VisibleRange.VISIBLE_RANGE_KEY)

  private def visibleRangeIn(editor: Editor, lookaround: Int): TextRange = {
    val visibleRectangle = editor.getScrollingModel.getVisibleArea

    val startOffset = {
      val position = editor.xyToLogicalPosition(visibleRectangle.getLocation)
      val adjustedPosition = new LogicalPosition((position.line - lookaround).max(0), 0)
      editor.logicalPositionToOffset(adjustedPosition)
    }

    val endOffset = {
      val position = editor.xyToLogicalPosition(new Point(visibleRectangle.x + visibleRectangle.width, visibleRectangle.y + visibleRectangle.height))
      val adjustedPosition = new LogicalPosition(position.line + lookaround + 1, 0)
      editor.logicalPositionToOffset(adjustedPosition)
    }

    TextRange.create(startOffset, startOffset.max(endOffset))
  }
}
