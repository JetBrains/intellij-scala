package org.jetbrains.plugins.scala.codeInsight.implicits.presentation
import java.awt.event.MouseEvent
import java.awt.{Color, Cursor, Font}

import com.intellij.openapi.editor.colors.EditorFontType
import com.intellij.openapi.editor.impl.EditorImpl
import com.intellij.openapi.editor.markup.TextAttributes
import com.intellij.openapi.util.SystemInfo

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
        new Text(fontMetrics(font).stringWidth(text), lineHeight, text, font, ascent),
        font, lineHeight, ascent, descent))

  def sequence(presentations: Presentation*): Presentation =
    new Sequence(lineHeight, presentations: _*)

  def attributes(transform: TextAttributes => TextAttributes, presentation: Presentation): Presentation =
    new Attributes(presentation, transform)

  def effects(font: Font, presentation: Presentation): Presentation =
    new Effect(presentation, font, lineHeight, ascent, descent)

  def background(color: Color, presentation: Presentation): Presentation =
    new Background(presentation)

  def rounding(arcWidth: Int, arcHeight: Int, presentation: Presentation): Presentation =
    new Rounding(presentation, arcWidth, arcHeight)

  def insets(left: Int, right: Int, presentation: Presentation): Presentation =
    new Sequence(lineHeight, new Space(left, lineHeight), presentation, new Space(right, lineHeight))

  def expansion(expanded: => Presentation, presentation: Presentation): Presentation = {
    val expansion = new Expansion(presentation, expanded)

    new OnClick(expansion, Button.Left, e => {
      if (!expansion.expanded) {
        expansion.expand(1)
        e.consume()
      }
    })
  }

  def navigation(decorator: Presentation => Presentation, onHover: Option[MouseEvent] => Unit, onClick: MouseEvent => Unit, presentation: Presentation): Presentation = {
    val inner = new OnClick(presentation, Button.Middle, onClick)

    val forwarding = new DynamicForwarding(inner)

    new OnHover(forwarding, isControlDown, e => {
      val cursor = if (e.isDefined) Cursor.HAND_CURSOR else Cursor.DEFAULT_CURSOR
      component.setCursor(Cursor.getPredefinedCursor(cursor))

      forwarding.delegate = if (e.isDefined) new OnClick(decorator(presentation), Button.Left, onClick) else inner

      onHover(e)
    })
  }

  private def isControlDown(e: MouseEvent): Boolean =
    SystemInfo.isMac && e.isMetaDown || e.isControlDown

  def synchronous(decorator: Presentation => Presentation, presentation1: Presentation, presentation2: Presentation): (Presentation, Presentation) = {
    val result = synchronous0(decorator, presentation1, presentation2)
    (result(0), result(1))
  }

  private def synchronous0(decorator: Presentation => Presentation, presentations: Presentation*): Seq[Presentation] = {
    val forwardings = presentations.map(new DynamicForwarding(_))

    forwardings.map(it => new OnHover(it, isControlDown, e => {
      forwardings.zip(presentations).foreach { case (forwarding, presentation) =>
        forwarding.delegate = if (e.isDefined) decorator(presentation) else presentation
      }
    }))
  }

  def onHover(handler: Option[MouseEvent] => Unit, presentation: Presentation): Presentation = {
    new OnHover(presentation, _ => true, handler)
  }

  def onClick(handler: MouseEvent => Unit, button: Button, presentation: Presentation): Presentation =
    new OnClick(presentation, button, handler)
}
