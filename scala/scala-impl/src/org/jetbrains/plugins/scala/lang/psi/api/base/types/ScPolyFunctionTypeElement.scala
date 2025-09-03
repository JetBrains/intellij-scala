package org.jetbrains.plugins.scala.lang.psi.api.base.types

import org.jetbrains.plugins.scala.lang.psi.api.toplevel.ScTypeParametersOwner

/**
 * Type element representing Scala 3 polymorphic function type,
 * e.g. `[A] => A => List[A]`
 */
trait ScPolyFunctionTypeElement extends ScTypeElement with ScTypeParametersOwner with ScDesugarizableTypeElement {
  override protected val typeName: String = "PolymorphicFunctionType"

  def resultTypeElement: Option[ScTypeElement]
}
