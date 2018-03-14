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

trait ScInfixPattern extends ScPattern {
  def leftPattern: ScPattern = findChildByClassScala(classOf[ScPattern])
  def rightPattern: Option[ScPattern] = findLastChild(classOf[ScPattern])
  def reference: ScStableCodeReferenceElement = findChildByClassScala(classOf[ScStableCodeReferenceElement])
}

object ScInfixPattern {
  def unapply(ifx: ScInfixPattern): Option[(ScPattern, ScStableCodeReferenceElement, Option[ScPattern])] =
    Some((ifx.leftPattern, ifx.reference, ifx.rightPattern))

}