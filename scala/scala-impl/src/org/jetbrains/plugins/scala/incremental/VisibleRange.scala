package org.jetbrains.plugins.scala
package incremental

import com.intellij.openapi.editor.{Editor, LogicalPosition}
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.util.{Key, TextRange}
import com.intellij.psi.PsiFile

import java.awt.Point

private object VisibleRange {
  private[incremental] val VISIBLE_RANGE_KEY = Key.create[TextRange]("editor_visible_range")

  private[incremental] val EXACT_VISIBLE_RANGE_KEY = Key.create[TextRange]("editor_exact_visible_range")

  private def lookaround: Int = Registry.intValue("scala.incremental.highlighting.lookaround")

  private[incremental] def isVisible(psiFile: PsiFile, range: TextRange): Boolean =
    editorsFor(psiFile).exists { editor =>
      val visibleRange = editor.getUserData(VISIBLE_RANGE_KEY)
      visibleRange == null || range.intersects(visibleRange) && !isFolded(editor, range)
    }

  private def isFolded(editor: Editor, range: TextRange): Boolean = {
    val foldingModel = editor.getFoldingModel
    val region1 = foldingModel.getCollapsedRegionAtOffset(range.getStartOffset)
    if (region1 == null) return false
    val region2 = foldingModel.getCollapsedRegionAtOffset(range.getEndOffset - 1)
    region1 == region2
  }

  private[incremental] def editorsFor(psiFile: PsiFile): Iterable[Editor] = {
    val document = psiFile.getViewProvider.getDocument // Cached
    if (document == null) return Seq.empty

    Highlighting.editors.filter(_.getDocument == document)
  }

  def saveIn(editor: Editor): Unit = {
    editor.putUserData(VisibleRange.VISIBLE_RANGE_KEY, visibleRangeIn(editor, lookaround))
    editor.putUserData(VisibleRange.EXACT_VISIBLE_RANGE_KEY, visibleRangeIn(editor, lookaround = 0))
  }

  def in(editor: Editor): TextRange =
    editor.getUserData(VisibleRange.VISIBLE_RANGE_KEY)

  def exactIn(editor: Editor): TextRange =
    editor.getUserData(VisibleRange.EXACT_VISIBLE_RANGE_KEY)

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
