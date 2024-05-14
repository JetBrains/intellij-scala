package org.jetbrains.plugins.scala.lang.psi.types.api

import org.jetbrains.plugins.scala.lang.psi.types.api.presentation.TypePresentation
import org.jetbrains.plugins.scala.lang.psi.types.{ConstraintSystem, ConstraintsResult, ScType}
import org.jetbrains.plugins.scala.project.ProjectContextOwner

trait TypeSystem extends ProjectContextOwner
  with Equivalence
  with Conformance
  with Bounds
  with PsiTypeBridge
  with TypePresentation {

  protected case class Key(left: ScType,
                           right: ScType,
                           flag: Boolean)

  val name: String

  def andType(types: Seq[ScType]): ScType

  def parameterizedType(designator: ScType, typeArguments: Seq[ScType]): ScType

  override final def clearCache(): Unit = {
    super[Equivalence].clearCache()
    super[Conformance].clearCache()
  }
}

object TypeSystem {

  private[api] def combine(result: ConstraintsResult)(constraints: ConstraintSystem): ConstraintsResult = result match {
    case system: ConstraintSystem =>
      if (constraints.isEmpty) system else system + constraints
    case _ => ConstraintsResult.Left
  }
}