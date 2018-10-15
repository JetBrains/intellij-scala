package org.jetbrains.plugins.scala.codeInsight.implicits.presentation
import java.awt.Graphics2D

import com.intellij.openapi.editor.markup.TextAttributes

class Space(override val width: Int, override val height: Int) extends Presentation {
  override def paint(g: Graphics2D, attributes: TextAttributes): Unit = {}
}
