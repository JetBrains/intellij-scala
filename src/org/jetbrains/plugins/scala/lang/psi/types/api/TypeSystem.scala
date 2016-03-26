package org.jetbrains.plugins.scala.lang.psi.types.api

import org.jetbrains.plugins.scala.lang.psi.types.ScType

/**
  * @author adkozlov
  */
trait TypeSystem {
  val name: String
  val equivalence: Equivalence
  val conformance: Conformance
  val bounds: Bounds
  val bridge: ScTypePsiTypeBridge
  protected val presentation: ScTypePresentation

  def presentableText = presentation.presentableText _

  def canonicalText = presentation.canonicalText _

  def urlText = presentation.urlText _
}

trait TypeSystemOwner {
  implicit val typeSystem: TypeSystem
}

trait TypeInTypeSystem extends ScType with TypeSystemOwner {
  override final def presentableText = typeSystem.presentableText(this)

  override final def canonicalText = typeSystem.canonicalText(this)
}
