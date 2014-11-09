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

trait ScTypeProjection extends ScTypeElement with ScReferenceElement {
  def typeElement = findChildByClassScala(classOf[ScTypeElement])
}