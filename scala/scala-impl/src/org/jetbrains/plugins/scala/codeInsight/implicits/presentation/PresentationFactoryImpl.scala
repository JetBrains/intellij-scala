package org.jetbrains.plugins.scala.codeInsight.implicits.presentation
import java.awt.event.MouseEvent
import java.awt.{Color, Font}

import com.intellij.openapi.editor.colors.EditorFontType
import com.intellij.openapi.editor.impl.EditorImpl
import com.intellij.openapi.editor.markup.TextAttributes

class PresentationFactoryImpl(editor: EditorImpl) extends PresentationFactory {
  private def ascent = editor.getAscent

  private def descent = editor.getDescent

  private def lineHeight = editor.getLineHeight

  private def charHeight = editor.getCharHeight

  private def fontMetrics(font: Font) = component.getFontMetrics(font)

  private def component = editor.getContentComponent

  private def font = editor.getColorsScheme.getFont(EditorFontType.PLAIN)

  override def space(width: Int): Presentation =
    new Space(width, editor.getLineHeight)

  override def text(text: String, font: Font): Presentation =
    new Background(
      new Effect(
        new Text(fontMetrics(font).stringWidth(text), lineHeight, text, font, None, ascent),
        font, lineHeight, ascent, descent),
      None)

  override def sequence(presentations: Presentation*): Presentation =
    new Sequence(lineHeight, presentations: _*)

  override def attributes(attributes: TextAttributes, presentation: Presentation): Presentation =
    new Attributes(presentation, attributes)

  override def effects(font: Font, presentation: Presentation): Presentation =
    new Effect(presentation, font, lineHeight, ascent, descent)

  override def background(color: Color, presentation: Presentation): Presentation =
    new Background(presentation, Some(color))

  override def rounding(arcWidth: Int, arcHeight: Int, presentation: Presentation): Presentation =
    new Rounding(presentation, arcWidth, arcHeight)

  override def insets(left: Int, right: Int, presentation: Presentation): Presentation =
    new Sequence(lineHeight, new Space(left, lineHeight), presentation, new Space(right, lineHeight))

  override def expansion(expanded: => Presentation, presentation: Presentation): Presentation =
    new Expansion(presentation, expanded)

  override def navigation(decorator: Presentation => Presentation, onHover: Option[MouseEvent] => Unit, onClick: MouseEvent => Unit, presentation: Presentation): Presentation =
    new Target(presentation, decorator(presentation), cursor => component.setCursor(cursor), onHover, onClick)

  override def synchronous(decorator: Presentation => Presentation, presentation1: Presentation, presentation2: Presentation): (Presentation, Presentation) = {
    val Seq(result1, result2) = SynchronousDecorator(decorator, presentation1, presentation2)
    (result1, result2)
  }

  override def onHover(handler: Option[MouseEvent] => Unit, presentation: Presentation): Presentation = {
    new OnHover(presentation, handler)
  }

  override def onRightClick(handler: MouseEvent => Unit, presentation: Presentation): Presentation =
    new OnRightClick(presentation, handler)
}
