package org.jetbrains.plugins.scala.lang.psi.types

/**
* @author ilyas
*/

case class ScFunctionType(returnType: ScType, params: Seq[ScType]) extends ScType

case class ScTupleType(components: Seq[ScType]) extends ScType