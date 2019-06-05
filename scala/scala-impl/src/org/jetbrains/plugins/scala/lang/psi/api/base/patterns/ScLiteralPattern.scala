package org.jetbrains.plugins.scala
package lang
package psi
package api
package base
package patterns

/**
* @author Alexander Podkhalyuzin
* Date: 28.02.2008
*/
trait ScLiteralPattern extends ScPattern {
  def getLiteral: ScLiteral = findChildByClassScala(classOf[ScLiteral])
}

object ScLiteralPattern {

  def unapply(pattern: ScLiteralPattern): Option[ScLiteral] =
    Option(pattern.getLiteral)
}