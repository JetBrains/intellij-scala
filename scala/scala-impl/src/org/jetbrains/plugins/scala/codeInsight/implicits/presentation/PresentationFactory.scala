package org.jetbrains.plugins.scala.codeInsight.implicits.presentation
import java.awt.event.MouseEvent
import java.awt.{Color, Font}

import com.intellij.openapi.editor.colors.EditorFontType
import com.intellij.openapi.editor.impl.EditorImpl
import com.intellij.openapi.editor.markup.TextAttributes

class PresentationFactory(editor: EditorImpl) {
  private def ascent = editor.getAscent

  private def descent = editor.getDescent

  private def lineHeight = editor.getLineHeight

  private def charHeight = editor.getCharHeight

  private def fontMetrics(font: Font) = component.getFontMetrics(font)

  private def component = editor.getContentComponent

  private def font = editor.getColorsScheme.getFont(EditorFontType.PLAIN)

  val empty: Presentation = space(0)

  def space(width: Int): Presentation =
    new Space(width, editor.getLineHeight)

  def text(text: String, font: Font): Presentation =
    new Background(
      new Effect(
        new Text(fontMetrics(font).stringWidth(text), lineHeight, text, font, None, ascent),
        font, lineHeight, ascent, descent),
      None)

  def sequence(presentations: Presentation*): Presentation =
    new Sequence(lineHeight, presentations: _*)

  def attributes(attributes: TextAttributes, presentation: Presentation): Presentation =
    new Attributes(presentation, attributes)

  def effects(font: Font, presentation: Presentation): Presentation =
    new Effect(presentation, font, lineHeight, ascent, descent)

  def background(color: Color, presentation: Presentation): Presentation =
    new Background(presentation, Some(color))

  def rounding(arcWidth: Int, arcHeight: Int, presentation: Presentation): Presentation =
    new Rounding(presentation, arcWidth, arcHeight)

  def insets(left: Int, right: Int, presentation: Presentation): Presentation =
    new Sequence(lineHeight, new Space(left, lineHeight), presentation, new Space(right, lineHeight))

  def expansion(expanded: => Presentation, presentation: Presentation): Presentation =
    new Expansion(presentation, expanded)

  def navigation(decorator: Presentation => Presentation, onHover: Option[MouseEvent] => Unit, onClick: MouseEvent => Unit, presentation: Presentation): Presentation =
    new Target(presentation, decorator(presentation), cursor => component.setCursor(cursor), onHover, onClick)

  def synchronous(decorator: Presentation => Presentation, presentation1: Presentation, presentation2: Presentation): (Presentation, Presentation) = {
    val Seq(result1, result2) = SynchronousDecorator(decorator, presentation1, presentation2)
    (result1, result2)
  }

  def onHover(handler: Option[MouseEvent] => Unit, presentation: Presentation): Presentation = {
    new OnHover(presentation, handler)
  }

  def onRightClick(handler: MouseEvent => Unit, presentation: Presentation): Presentation =
    new OnRightClick(presentation, handler)
}
