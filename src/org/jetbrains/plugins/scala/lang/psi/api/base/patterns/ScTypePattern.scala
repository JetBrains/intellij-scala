package org.jetbrains.plugins.scala
package lang
package psi
package api
package base
package patterns

import org.jetbrains.plugins.scala.lang.psi.ScalaPsiElement
import org.jetbrains.plugins.scala.lang.psi.api.base.types.ScTypeElement

/** 
* @author Alexander Podkhalyuzin
* Date: 28.02.2008
*/

trait ScTypePattern extends ScalaPsiElement {
  def typeElement = findChildByClassScala(classOf[ScTypeElement])
}