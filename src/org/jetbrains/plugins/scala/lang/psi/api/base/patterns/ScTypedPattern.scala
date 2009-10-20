package org.jetbrains.plugins.scala
package lang
package psi
package api
package base
package patterns

import org.jetbrains.plugins.scala.lang.psi.types._

/**
* @author Alexander Podkhalyuzin
*/

trait ScTypedPattern extends ScBindingPattern  {
  def typePattern = findChild(classOf[ScTypePattern])

  override def calcType = typePattern match {
    case None => Nothing
    case Some(tp) => tp.typeElement.cachedType.unwrap(Any)
  }
}