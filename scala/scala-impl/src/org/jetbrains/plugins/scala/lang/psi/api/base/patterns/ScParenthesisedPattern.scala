package org.jetbrains.plugins.scala
package lang
package psi
package api
package base
package patterns

import org.jetbrains.plugins.scala.extensions.ObjectExt

trait ScParenthesisedPattern extends ScPattern with ScParenthesizedElement {
  type Kind = ScPattern

  override def innerElement: Option[ScPattern] = findChild[ScPattern]

  override def sameTreeParent: Option[ScPattern] = getParent.asOptionOf[ScPattern]
}

object ScParenthesisedPattern {
  def unapply(e: ScParenthesisedPattern): Option[ScPattern] = e.innerElement
}