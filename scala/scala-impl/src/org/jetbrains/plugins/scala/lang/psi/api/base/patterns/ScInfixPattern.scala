package org.jetbrains.plugins.scala.lang.psi.api.base
package patterns

trait ScInfixPattern extends ScExtractorPattern with ScInfixElement {
  type Kind = ScPattern
  type Reference = ScStableCodeReference

  override def left: ScPattern = findChild[ScPattern].get
  override def operation: ScStableCodeReference = findChild[ScStableCodeReference].get
  override def rightOption: Option[ScPattern] = findLastChild[ScPattern]
  override def ref: ScStableCodeReference = operation
}

object ScInfixPattern {
  def unapply(ifx: ScInfixPattern): Option[(ScPattern, ScStableCodeReference, Option[ScPattern])] =
    Some((ifx.left, ifx.operation, ifx.rightOption))

}
