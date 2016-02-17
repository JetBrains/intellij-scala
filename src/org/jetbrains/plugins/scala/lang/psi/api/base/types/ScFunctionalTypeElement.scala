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

trait ScFunctionalTypeElement extends ScTypeElement {
  override protected val typeName = "FunctionalType"

  def paramTypeElement = findChildByClassScala(classOf[ScTypeElement])

  def returnTypeElement = findChildrenByClassScala(classOf[ScTypeElement]) match {
    case Array(single) => None
    case many => Some(many(1))
  }
}