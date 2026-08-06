package org.jetbrains.plugins.scala.lang.psi.api.base.types

import org.jetbrains.plugins.scala.lang.psi.api.base.ScNamedTupleComponent

trait ScNamedTupleTypeComponent extends ScNamedTupleComponent with ScTypeElement {
  final def namedTuple: ScNamedTupleTypeElement = getParent.asInstanceOf[ScNamedTupleTypeElement]

  final def typeElement: Option[ScTypeElement] = findChild[ScTypeElement]
}
