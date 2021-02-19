package org.jetbrains.plugins.scala.lang.psi.api.base.types

import org.jetbrains.plugins.scala.lang.psi.api._
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.{ScTypeParametersOwner, ScTypeParametersOwnerBase}

/**
 * Type element representing Scala 3 polymorphic function type,
 * e.g. `[A] => A => List[A]`
 */
trait ScPolyFunctionTypeElementBase extends ScTypeElementBase with ScTypeParametersOwnerBase { this: ScPolyFunctionTypeElement =>
  override protected val typeName: String = "PolymorhicFunctionType"

  def resultTypeElement: Option[ScTypeElement]
}