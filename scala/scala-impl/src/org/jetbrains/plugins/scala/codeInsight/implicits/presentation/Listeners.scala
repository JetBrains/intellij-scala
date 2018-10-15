package org.jetbrains.plugins.scala.codeInsight.implicits.presentation

import java.awt.{Dimension, Rectangle}

trait Listeners {
  private var listeners = List.empty[PresentationListener]

  def addPresentationListener(listener: PresentationListener): Unit =
    listeners ::= listener

  def removePresentationListener(listener: PresentationListener): Unit =
    listeners = listeners.filterNot(_ == listener)

  protected def fireContentChanged(area: Rectangle): Unit =
    listeners.foreach(_.contentChanged(area))

  protected def fireSizeChanged(previous: Dimension, current: Dimension): Unit =
    listeners.foreach(_.sizeChanged(previous, current))
}
