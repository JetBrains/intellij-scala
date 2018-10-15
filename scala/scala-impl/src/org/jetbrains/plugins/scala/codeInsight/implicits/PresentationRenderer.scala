package org.jetbrains.plugins.scala.codeInsight.implicits

import java.awt.{Graphics, Graphics2D, Rectangle}

import com.intellij.codeInsight.daemon.impl.HintRenderer
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.markup.TextAttributes
import org.jetbrains.plugins.scala.codeInsight.implicits.presentation.Presentation

class PresentationRenderer(val presentation: Presentation) extends HintRenderer(null) { // todo text
  override def calcWidthInPixels(editor: Editor): Int = presentation.width

  override def paint(editor: Editor, g: Graphics, r: Rectangle, textAttributes: TextAttributes): Unit = {
    val g2d = g.asInstanceOf[Graphics2D]

    // todo try finally
    g2d.translate(r.x, r.y)
    presentation.paint(g2d, getTextAttributes(editor)) // todo use textAttributes
    g2d.translate(-r.x, -r.y)
  }
}
