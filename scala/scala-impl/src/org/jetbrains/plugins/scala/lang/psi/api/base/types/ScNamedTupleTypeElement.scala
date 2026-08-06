package org.jetbrains.plugins.scala.lang.psi.api.base.types

trait ScNamedTupleTypeElement extends ScTypeElement {
  override protected val typeName = "NamedTupleType"

  final def components: Seq[ScNamedTupleTypeComponent] = findChildren[ScNamedTupleTypeComponent]
}

object ScNamedTupleTypeElement {
  def unapply(e: ScNamedTupleTypeElement): Some[Seq[ScNamedTupleTypeComponent]] =
    Some(e.components)
}