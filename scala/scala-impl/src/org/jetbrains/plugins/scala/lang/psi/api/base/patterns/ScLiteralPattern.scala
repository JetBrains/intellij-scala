package org.jetbrains.plugins.scala.lang.psi.api.base
package patterns

trait ScLiteralPattern extends ScPattern {
  def getLiteral: ScLiteral = findChild[ScLiteral].get
}

object ScLiteralPattern {

  def unapply(pattern: ScLiteralPattern): Option[ScLiteral] =
    Option(pattern.getLiteral)
}