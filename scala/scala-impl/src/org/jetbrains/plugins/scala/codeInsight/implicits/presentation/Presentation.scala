package org.jetbrains.plugins.scala.codeInsight.implicits.presentation

import java.awt.Graphics2D

import com.intellij.openapi.editor.markup.TextAttributes

trait Presentation extends InputEvents with Listeners {
  def width: Int

  def height: Int

  def paint(g: Graphics2D, attributes: TextAttributes): Unit

  def expand(level: Int): Unit = {}
}
