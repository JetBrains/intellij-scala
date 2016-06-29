package org.jetbrains.plugins.dotty.lang.psi.api.base.types

import org.jetbrains.plugins.scala.lang.psi.api.base.types.{ScRefinement, ScTypeElement}

/**
  * @author adkozlov
  */
trait DottyRefinedTypeElement extends ScTypeElement {
  override val typeName = "RefinedType"

  def typeElement: ScTypeElement = findChildByClassScala(classOf[ScTypeElement])

  def refinement: ScRefinement = findChildByClassScala(classOf[ScRefinement])
}
