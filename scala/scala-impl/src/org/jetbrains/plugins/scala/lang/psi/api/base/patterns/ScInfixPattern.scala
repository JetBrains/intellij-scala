package org.jetbrains.plugins.scala
package lang
package psi
package api
package base
package patterns

import org.jetbrains.plugins.scala.lang.psi.api._


/**
* @author Alexander Podkhalyuzin
* Date: 28.02.2008
*/

trait ScInfixPatternBase extends ScPatternBase with ScInfixElementBase { this: ScInfixPattern =>
  type Kind = ScPattern
  type Reference = ScStableCodeReference

  override def left: ScPattern = findChild[ScPattern].get
  override def operation: ScStableCodeReference = findChild[ScStableCodeReference].get
  override def rightOption: Option[ScPattern] = findLastChild[ScPattern]
}

abstract class ScInfixPatternCompanion {
  def unapply(ifx: ScInfixPattern): Option[(ScPattern, ScStableCodeReference, Option[ScPattern])] =
    Some((ifx.left, ifx.operation, ifx.rightOption))

}