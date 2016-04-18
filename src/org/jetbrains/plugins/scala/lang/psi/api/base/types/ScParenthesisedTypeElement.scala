package org.jetbrains.plugins.scala
package lang
package psi
package api
package base
package types

/** 
* @author Alexander Podkhalyuzin
* Date: 13.03.2008
*/

trait ScParenthesisedTypeElement extends ScTypeElement {
  override protected val typeName = "TypeInParenthesis"

  def typeElement: Option[ScTypeElement] = findChild(classOf[ScTypeElement])
}