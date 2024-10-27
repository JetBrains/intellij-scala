package org.jetbrains.plugins.scala.lang.psi.api.base.types

import org.jetbrains.plugins.scala.lang.psi.api.statements.params.ScParameterClause

/**
 * Type element representing Scala 3 dependent function type,
 * e.g. `trait Entry { type Key; val key: Key }; (e: Entry) => e.Key`
 */
trait ScDependentFunctionTypeElement extends ScTypeElement {
  override protected val typeName: String = "DependentFunctionType"

  def parameterClause: ScParameterClause
  def returnTypeElement: Option[ScTypeElement]
}

object ScDependentFunctionTypeElement {
  def unapply(arg: ScDependentFunctionTypeElement): Some[(ScParameterClause, Option[ScTypeElement])] =
    Some(arg.parameterClause, arg.returnTypeElement)
}
