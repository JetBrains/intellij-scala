package org.jetbrains.plugins.scala.lang.psi.types.api

import com.intellij.psi.{PsiNamedElement, PsiTypeParameter}
import org.jetbrains.plugins.scala.lang.psi.api.statements.params.TypeParamIdOwner
import org.jetbrains.plugins.scala.lang.psi.light.DummyLightTypeParam
import org.jetbrains.plugins.scala.lang.psi.types.api.TypeParameterType.isMaskedExtensionTypeParameter
import org.jetbrains.plugins.scala.lang.psi.types.api.designator.DesignatorOwner
import org.jetbrains.plugins.scala.lang.psi.types.nonvalue.ScTypePolymorphicType
import org.jetbrains.plugins.scala.lang.psi.types.{ConstraintSystem, ConstraintsResult, LeafType, NamedType, ScType, ScalaTypeVisitor}
import org.jetbrains.plugins.scala.project.ProjectContext
import org.jetbrains.plugins.scala.util.ScEquivalenceUtil

class TypeParameterType private (val typeParameter: TypeParameter)
  extends DesignatorOwner with ValueType with NamedType with LeafType {

  override implicit def projectContext: ProjectContext = psiTypeParameter

  def typeParameters: Seq[TypeParameter] = typeParameter.typeParameters

  val arguments: Seq[TypeParameterType] = typeParameters.map(new TypeParameterType(_))

  def lowerType: ScType = typeParameter.lowerType

  def upperType: ScType = typeParameter.upperType

  def psiTypeParameter: PsiTypeParameter = typeParameter.psiTypeParameter

  override val name: String = typeParameter.name

  def isInvariant: Boolean     = typeParameter.isInvariant
  def isCovariant: Boolean     = typeParameter.isCovariant
  def isContravariant: Boolean = typeParameter.isContravariant

  final def variance: Variance =
    if (isCovariant) Covariant
    else if (isContravariant) Contravariant
    else if (isInvariant) Invariant
    else Bivariant

  override def equivInner(
    `type`:      ScType,
    constraints: ConstraintSystem,
    falseUndef:  Boolean
  ): ConstraintsResult =
    `type` match {
      case that: TypeParameterType =>
        if (that.psiTypeParameter eq psiTypeParameter) constraints
        else {
          /** see
           * [[org.jetbrains.plugins.scala.lang.psi.implicits.ImplicitCollector#maskTypeParametersInExtensions]]
           */
          if (isMaskedExtensionTypeParameter(this, that)
            || isMaskedExtensionTypeParameter(that, this))
            constraints
          else ConstraintsResult.Left
        }
      case tpt: ScTypePolymorphicType =>
        ScEquivalenceUtil
          .isDesignatorEqiuivalentToPolyType(this, tpt, constraints, falseUndef)
          .getOrElse(ConstraintsResult.Left)
      case _ => ConstraintsResult.Left
    }

  override def visitType(visitor: ScalaTypeVisitor): Unit = visitor.visitTypeParameterType(this)

  override def equals(other: Any): Boolean = other match {
    case that: TypeParameterType => typeParameter == that.typeParameter
    case _ => false
  }

  override def hashCode(): Int = typeParameter.hashCode()

  override val element: PsiNamedElement = typeParameter.psiTypeParameter
}

object TypeParameterType {
  private def isMaskedExtensionTypeParameter(lhs: TypeParameterType, rhs: TypeParameterType): Boolean =
    lhs.psiTypeParameter match {
      case _: DummyLightTypeParam => lhs.lowerType.equiv(rhs) && lhs.upperType.equiv(rhs)
      case _                      => false
    }

  def apply(tp: TypeParameter): TypeParameterType =
    new TypeParameterType(tp)

  def apply(psiTp: PsiTypeParameter): TypeParameterType =
    new TypeParameterType(TypeParameter(psiTp))

  def unapply(tpt: TypeParameterType): Option[TypeParameter] = Some(tpt.typeParameter)

  object ofPsi {
    def unapply(tpt: TypeParameterType): Option[PsiTypeParameter] = Some(tpt.psiTypeParameter)
  }
}
