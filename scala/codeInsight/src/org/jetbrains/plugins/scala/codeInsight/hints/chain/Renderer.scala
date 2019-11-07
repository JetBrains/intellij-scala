package org.jetbrains.plugins.scala.codeInsight.hints.chain

import java.awt.{Graphics, Insets, Rectangle}

import com.intellij.openapi.editor.markup.TextAttributes
import com.intellij.openapi.editor.{Editor, Inlay}
import org.jetbrains.plugins.scala.annotator.hints.Text
import org.jetbrains.plugins.scala.codeInsight.hints.chain.Renderer._
import org.jetbrains.plugins.scala.codeInsight.implicits.TextPartsHintRenderer

private class Renderer(val line: Line, textParts: Seq[Text], onRepaint: Editor => Unit) extends TextPartsHintRenderer(textParts, Some(typeHintsMenu)) {
  private var cached = Cached(0, 0)

  def setMargin(lineEndX: Int, margin: Int, inlay: Inlay[_]): Unit = {
    if (cached.margin != margin) {
      cached = Cached(lineEndX, margin)
      inlay.updateSize()
    }
  }

  override def paint(editor: Editor, g: Graphics, r: Rectangle, textAttributes: TextAttributes): Unit = {
    if (cached.lineEndX != line.lineEndXIn(editor)) {
      val oldMargin = cached.margin
      onRepaint(editor)
      // after recalculating the offset, r has the wrong width, so we fix that here
      r.width += cached.margin - oldMargin
    }

    super.paint(editor, g, r, textAttributes)
  }

  override def getMargin(editor: Editor): Insets = new Insets(0, cached.margin, 0, 0)
}

private object Renderer {
  private case class Cached(lineEndX: Int, margin: Int)
}