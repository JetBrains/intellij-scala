package org.jetbrains.plugins.scala.lang.psi.stubs.elements.signatures

import org.jetbrains.plugins.scala.lang.psi.api.statements.params.ScParameter
import org.jetbrains.plugins.scala.lang.psi.stubs.elements.ScStubElementType

abstract class ScParamElementType[P <: ScParameter](debugName: String)
  extends ScStubElementType[ScParameter](debugName)
