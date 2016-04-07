package org.jetbrains.plugins.scala.lang.psi.types

/**
  * @author adkozlov
  */
object ScalaTypeSystem extends api.TypeSystem {
  override val name = "Scala"
  override lazy val equivalence = Equivalence
  override lazy val conformance = Conformance
  override lazy val bounds = Bounds
  override lazy val bridge = ScTypePsiTypeBridge
  protected override lazy val presentation = ScTypePresentation

  override def andType(types: Seq[ScType]) = ScCompoundType(types)

  override def parameterizedType(designator: ScType, typeArguments: Seq[ScType]) =
    ScParameterizedType(designator, typeArguments)
}
