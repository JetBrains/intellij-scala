package org.jetbrains.plugins.scala.codeInsight.implicits.presentation

import java.awt.Graphics2D

import com.intellij.openapi.editor.markup.TextAttributes
import org.jetbrains.plugins.scala.codeInsight.implicits._

class Attributes(presentation: Presentation, attributes: TextAttributes) extends DynamicPresentation(presentation) {
  override def paint(g: Graphics2D, attributes0: TextAttributes): Unit = {
    presentation.paint(g, attributes0 + attributes)
  }
}
