package org.jetbrains.plugins.scala
package lang
package psi
package api
package statements
package params

import org.jetbrains.plugins.scala.lang.psi.adapters.PsiTypeParameterAdapter
import org.jetbrains.plugins.scala.lang.psi.api.base.ScAnnotationsHolder
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.{ScNamedElement, ScPolymorphicElement, ScTypeParametersOwner}
import org.jetbrains.plugins.scala.lang.psi.types.api._

trait ScTypeParam extends ScNamedElement with ScPolymorphicElement with PsiTypeParameterAdapter with ScAnnotationsHolder {
  val typeParamId: Long

  def isCovariant: Boolean

  def isContravariant: Boolean

  final def variance: Variance = if (isCovariant) Covariant else if (isContravariant) Contravariant else Invariant

  def owner: ScTypeParametersOwner

  def getContainingFileName: String

  def typeParameterText: String

  def isHigherKindedTypeParameter: Boolean

  override def hasAnnotation(qualifiedName: String): Boolean = super.hasAnnotation(qualifiedName)
}