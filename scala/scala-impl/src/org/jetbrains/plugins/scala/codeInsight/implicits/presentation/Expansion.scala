package org.jetbrains.plugins.scala.codeInsight.implicits.presentation

class Expansion(presentation: Presentation, expansion: => Presentation) extends DynamicForwarding(presentation) {
  private lazy val expandedPresentation = expansion

  def expanded: Boolean = delegate != presentation

  override def expand(level: Int): Unit = {
    delegate = if (level > 0) expandedPresentation else presentation
    delegate.expand(0.max(level - 1))
  }
}
