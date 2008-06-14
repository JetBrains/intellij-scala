package org.jetbrains.plugins.scala.lang.psi.types

/**
* @author ilyas
*/

case class ScFunctionType(returnType: ScType, params: Seq[ScType]) extends ScType {
  override def equiv(that : ScType) = that match {
    case ScFunctionType(rt1, params1) => (returnType equiv rt1) &&
                               params.equalsWith(params1) {_ equiv _}
    case _ => false
  }
}

case class ScTupleType(components: Seq[ScType]) extends ScType {
  override def equiv(that : ScType) = that match {
    case ScTupleType(c1) => components.equalsWith(c1) {_ equiv _}
    case _ => false
  }
}