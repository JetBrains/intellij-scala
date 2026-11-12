package org.jetbrains.plugins.scala.lang.psi.api.statements
package params

import org.jetbrains.plugins.scala.lang.psi.adapters.PsiTypeParameterAdapter
import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenType
import org.jetbrains.plugins.scala.lang.psi.api.base.ScAnnotationsHolder
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.{ScNamedElement, ScPolymorphicElement, ScTypeParametersOwner}
import org.jetbrains.plugins.scala.lang.psi.types.api._

trait ScTypeParam extends ScNamedElement with ScPolymorphicElement with PsiTypeParameterAdapter with ScAnnotationsHolder {
  def typeParamId: Long

  def isCovariant: Boolean

  def isContravariant: Boolean

  final def variance: Variance = if (isCovariant) Covariant else if (isContravariant) Contravariant else Invariant

  def owner: ScTypeParametersOwner

  def getContainingFileName: String

  def typeParameterText: String

  def isHigherKindedTypeParameter: Boolean

  /**
   * Syntax-only marker. Like the capture-bound accessors, this may load the AST
   * for a stub-backed parameter; capture-set type semantics are tracked in SCL-26427.
   */
  def isCaptureSetParameter: Boolean = findFirstChildByType(ScalaTokenType.CaptureOperator).nonEmpty

  override def hasAnnotation(qualifiedName: String): Boolean = super.hasAnnotation(qualifiedName)
}
