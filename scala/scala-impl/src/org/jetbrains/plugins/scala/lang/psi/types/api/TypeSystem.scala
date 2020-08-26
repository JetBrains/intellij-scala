package org.jetbrains.plugins.scala.lang.psi
package types
package api

import org.jetbrains.plugins.scala.project.ProjectContextOwner

/**
  * @author adkozlov
  */
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

  def andType(types: collection.Seq[ScType]): ScType

  def parameterizedType(designator: ScType, typeArguments: collection.Seq[ScType]): ValueType

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