package org.jetbrains.plugins.scala.codeInsight.implicits.presentation

import java.awt.event.MouseEvent
import java.awt.{Color, Font, Point}

import com.intellij.openapi.editor.markup.TextAttributes

trait PresentationFactory {
  def empty: Presentation = space(0)

  def space(width: Int): Presentation

  def text(text: String, font: Font): Presentation

  def sequence(presentations: Presentation*): Presentation

  def attributes(attributes: TextAttributes, presentation: Presentation): Presentation

  def effects(font: Font, presentation: Presentation): Presentation

  def background(color: Color, presentation: Presentation): Presentation

  def rounding(arcWidth: Int, arcHeight: Int, presentation: Presentation): Presentation

  def insets(left: Int, right: Int, presentation: Presentation): Presentation

  def expansion(expanded: => Presentation, presentation: Presentation): Presentation

  def navigation(decorator: Presentation => Presentation, onHover: Option[MouseEvent] => Unit, onClick: MouseEvent => Unit, presentation: Presentation): Presentation

  def synchronous(decorator: Presentation => Presentation, presentation1: Presentation, presentation2: Presentation): (Presentation, Presentation)

  def onHover(handler: Option[MouseEvent] => Unit, presentation: Presentation): Presentation
}
