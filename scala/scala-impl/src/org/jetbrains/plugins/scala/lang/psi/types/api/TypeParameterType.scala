package org.jetbrains.plugins.scala
package lang
package psi
package types
package api

import com.intellij.psi.PsiTypeParameter
import org.jetbrains.plugins.scala.lang.psi.api.statements.params.ScTypeParam
import org.jetbrains.plugins.scala.project.ProjectContext

class TypeParameterType private (val typeParameter: TypeParameter)
  extends ValueType with NamedType with LeafType {

  override implicit def projectContext: ProjectContext = psiTypeParameter

  def typeParameters: collection.Seq[TypeParameter] = typeParameter.typeParameters

  val arguments: collection.Seq[TypeParameterType] = typeParameters.map(new TypeParameterType(_))

  def lowerType: ScType = typeParameter.lowerType

  def upperType: ScType = typeParameter.upperType

  def psiTypeParameter: PsiTypeParameter = typeParameter.psiTypeParameter

  override val name: String = typeParameter.name

  def isInvariant: Boolean = typeParameter.isInvariant
  def isCovariant: Boolean = typeParameter.isCovariant
  def isContravariant: Boolean = typeParameter.isContravariant
  final def variance: Variance =
    if (isCovariant) Covariant
    else if (isContravariant) Contravariant
    else if (isInvariant) Invariant
    else Bivariant

  override def equivInner(`type`: ScType, constraints: ConstraintSystem, falseUndef: Boolean): ConstraintsResult = {
    val success = `type` match {
      case that: TypeParameterType => (that.psiTypeParameter eq psiTypeParameter) || {
        (psiTypeParameter, that.psiTypeParameter) match {
          case (myBound: ScTypeParam, thatBound: ScTypeParam) =>
            //TODO this is a temporary hack, so ignore substitutor for now
            myBound.lowerBound.exists(_.equiv(thatBound.lowerBound.getOrNothing)) &&
              myBound.upperBound.exists(_.equiv(thatBound.upperBound.getOrNothing)) &&
              (myBound.name == thatBound.name || thatBound.isHigherKindedTypeParameter || myBound.isHigherKindedTypeParameter)
          case _ => false
        }
      }
      case _ => false
    }
    if (success) constraints
    else ConstraintsResult.Left
  }

  override def visitType(visitor: ScalaTypeVisitor): Unit = visitor.visitTypeParameterType(this)

  override def equals(other: Any): Boolean = other match {
    case that: TypeParameterType => typeParameter == that.typeParameter
    case _ => false
  }

  override def hashCode(): Int = typeParameter.hashCode()
}

object TypeParameterType {
  def apply(tp: TypeParameter): TypeParameterType =
    new TypeParameterType(tp)

  def apply(psiTp: PsiTypeParameter): TypeParameterType =
    new TypeParameterType(TypeParameter(psiTp))

  def unapply(tpt: TypeParameterType): Option[TypeParameter] = Some(tpt.typeParameter)

  object ofPsi {
    def unapply(tpt: TypeParameterType): Option[PsiTypeParameter] = Some(tpt.psiTypeParameter)
  }
}
