package org.jetbrains.plugins.scala.lang.psi.types.api

import org.jetbrains.plugins.scala.lang.psi.ElementScope
import org.jetbrains.plugins.scala.lang.psi.types.ScType
import org.jetbrains.plugins.scala.project.{ProjectContext, ProjectExt}

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

  final def presentableText: (ScType, Boolean) => String = presentation.presentableText

  final def canonicalText: ScType => String = presentation.canonicalText

  final def urlText: ScType => String = presentation.urlText

  def andType(types: Seq[ScType]): ScType

  def parameterizedType(designator: ScType, typeArguments: Seq[ScType]): ValueType
}

object TypeSystem extends LowerPriority {
  implicit def fromProjectContext(implicit projectContext: ProjectContext): TypeSystem = projectContext.project.typeSystem
}

trait LowerPriority {
  implicit def fromElementScope(implicit scope: ElementScope): TypeSystem = scope.project.typeSystem
}

trait TypeSystemOwner {
  implicit val typeSystem: TypeSystem
}

trait TypeInTypeSystem extends ScType with TypeSystemOwner {
  override final def presentableText: String = typeSystem.presentableText(this, true)

  override final def canonicalText: String = typeSystem.canonicalText(this)
}
