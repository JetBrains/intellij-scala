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

trait ScInfixPattern extends ScPattern with ScGenericInfixNode[ScPattern] {
  def leftPattern: ScPattern = findChildByClassScala(classOf[ScPattern])
  def rightPattern: Option[ScPattern] = findLastChild(classOf[ScPattern])
  def reference: ScStableCodeReferenceElement = findChildByClassScala(classOf[ScStableCodeReferenceElement])

  override def rightOperand: Option[ScPattern] = rightPattern
  override def leftOperand: ScPattern = leftPattern
  override def operation: ScReferenceElement = reference
}

object ScInfixPattern {
  def unapply(ifx: ScInfixPattern): Option[(ScPattern, ScStableCodeReferenceElement, Option[ScPattern])] =
    Some((ifx.leftPattern, ifx.reference, ifx.rightPattern))

}