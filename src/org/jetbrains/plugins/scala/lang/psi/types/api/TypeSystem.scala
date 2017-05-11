package org.jetbrains.plugins.scala.lang.psi.types.api

import org.jetbrains.plugins.scala.lang.psi.types.ScType
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

  val name: String

  def andType(types: Seq[ScType]): ScType

  def parameterizedType(designator: ScType, typeArguments: Seq[ScType]): ValueType

  override final def clearCache(): Unit = {
    super[Equivalence].clearCache()
    super[Conformance].clearCache()
  }
}