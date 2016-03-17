package org.jetbrains.plugins.scala.lang.psi.types.api

/**
  * @author adkozlov
  */
trait TypeSystem {
  val name: String
  val equivalence: Equivalence
  val conformance: Conformance
  val bounds: Bounds
  val bridge: ScTypePsiTypeBridge
}

trait TypeSystemOwner {
  implicit val typeSystem: TypeSystem
}
