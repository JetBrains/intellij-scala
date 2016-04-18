package org.jetbrains.plugins.scala
package lang
package psi
package api
package base
package types

import org.jetbrains.plugins.scala.lang.psi.types.result.TypingContext

/** 
* @author Alexander Podkhalyuzin
* Date: 07.03.2008
*/

trait ScAnnotTypeElement extends ScTypeElement {
  override protected val typeName = "TypeWithAnnotation"

  def typeElement = findChildByClassScala(classOf[ScTypeElement])

  protected def innerType(ctx: TypingContext) = typeElement.getType(ctx)
}