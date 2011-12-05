package org.jetbrains.plugins.scala
package lang
package psi
package api
package base
package types

/**
* @author Alexander Podkhalyuzin
* Date: 07.03.2008
*/
trait ScTupleTypeElement extends ScTypeElement {
  def typeList = findChildByClassScala(classOf[ScTypes])

  def components = typeList.types
}