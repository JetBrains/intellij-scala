package org.jetbrains.plugins.scala.codeInsight.implicits.presentation

import java.awt.Graphics2D

import com.intellij.openapi.editor.markup.TextAttributes

class Attributes(presentation: Presentation, transform: TextAttributes => TextAttributes) extends StaticForwarding(presentation) {
  override def paint(g: Graphics2D, attributes: TextAttributes): Unit = {
    presentation.paint(g, transform(attributes))
  }
}
