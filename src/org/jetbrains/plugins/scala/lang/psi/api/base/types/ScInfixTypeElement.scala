package org.jetbrains.plugins.scala
package lang
package psi
package api
package base
package types

/** 
* @author Alexander Podkhalyuzin
* Date: 22.02.2008
*/

trait ScInfixTypeElement extends ScTypeElement {
  override protected val typeName = "InfixType"

  def lOp : ScTypeElement = findChildByClassScala(classOf[ScTypeElement])
  def rOp : Option[ScTypeElement] 
  def ref : ScStableCodeReferenceElement = findChildByClassScala(classOf[ScStableCodeReferenceElement])
}