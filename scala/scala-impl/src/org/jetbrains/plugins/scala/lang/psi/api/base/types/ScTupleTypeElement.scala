package org.jetbrains.plugins.scala.lang.psi.api.base
package types

trait ScTupleTypeElement extends ScDesugarizableToParametrizedTypeElement {
  override protected val typeName = "TupleType"

  def typeList: ScTypes = findChild[ScTypes].get

  def components: Seq[ScTypeElement] = typeList.types
}

object ScTupleTypeElement {
  def unapplySeq(e: ScTupleTypeElement): Some[Seq[ScTypeElement]] = Some(e.components)
}