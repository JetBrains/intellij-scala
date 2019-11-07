package org.jetbrains.plugins.scala.codeInsight.hints.chain

import com.intellij.openapi.Disposable
import com.intellij.openapi.editor.{Document, Editor, RangeMarker}

private class Line(offset: Int, val hint: Option[Hint])(document: Document) extends Disposable {
  private val marker: RangeMarker = document.createRangeMarker(offset, offset)

  def lineEndXIn(editor: Editor): Int =
    editor.offsetToXY(document.getLineEndOffset(document.getLineNumber(marker.getEndOffset)), true, false).x

  override def dispose(): Unit = marker.dispose()
}
