package org.jetbrains.plugins.scala.lang.psi.types.api

import com.intellij.psi.PsiTypeParameter
import org.jetbrains.plugins.scala.Suspension
import org.jetbrains.plugins.scala.extensions.PsiNamedElementExt
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiUtil.getPsiElementId
import org.jetbrains.plugins.scala.lang.psi.api.statements.params.ScTypeParam
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiManager
import org.jetbrains.plugins.scala.lang.psi.types.{NamedType, ScSubstitutor, ScType, ScUndefinedSubstitutor}

/**
  * Class representing type parameters in our type system. Can be constructed from psi.
  * todo: lower and upper types will be reevaluated many times, is it good or bad? Seems bad. What other ways to fix SCL-7216?
  *
  * @param lowerType important to be lazy, see SCL-7216
  * @param upperType important to be lazy, see SCL-7216
  */
case class TypeParameter(name: String,
                         typeParameters: Seq[TypeParameter],
                         lowerType: () => ScType,
                         upperType: () => ScType,
                         psiTypeParameter: PsiTypeParameter) {
  def nameAndId = getPsiElementId(psiTypeParameter)

  def update(function: ScType => ScType): TypeParameter = TypeParameter(name,
    typeParameters.map(_.update(function)), {
      val result = function(lowerType())
      () => result
    }, {
      val result = function(upperType())
      () => result
    }, psiTypeParameter)

  def updateWithVariance(function: (ScType, Int) => ScType, variance: Int): TypeParameter = TypeParameter(name,
    typeParameters.map(_.updateWithVariance(function, variance)), {
      val result = function(lowerType(), variance)
      () => result
    }, {
      val result = function(upperType(), -variance)
      () => result
    }, psiTypeParameter)

  def canEqual(other: Any): Boolean = other.isInstanceOf[TypeParameter]

  override def equals(other: Any): Boolean = other match {
    case that: TypeParameter =>
      (that canEqual this) &&
        name == that.name &&
        typeParameters == that.typeParameters &&
        lowerType() == that.lowerType() &&
        upperType() == that.upperType() &&
        psiTypeParameter == that.psiTypeParameter
    case _ => false
  }

  override def hashCode() = Seq(name, typeParameters, psiTypeParameter)
    .map(_.hashCode())
    .foldLeft(0)((a, b) => 31 * a + b)
}

object TypeParameter {
  def apply(typeParameter: PsiTypeParameter): TypeParameter = {
    val (typeParameters, maybeLower, maybeUpper) = typeParameter match {
      case typeParam: ScTypeParam =>
        (typeParam.typeParameters, typeParam.lowerBound.toOption, typeParam.upperBound.toOption)
      case _ =>
        (Seq.empty, None, None)
    }
    TypeParameter(typeParameter.name,
      typeParameters.map(TypeParameter(_)),
      () => maybeLower.getOrElse(Nothing),
      () => maybeUpper.getOrElse(Any),
      typeParameter)
  }
}

case class TypeParameterType(name: String,
                             arguments: Seq[TypeParameterType],
                             lowerType: Suspension[ScType],
                             upperType: Suspension[ScType],
                             psiTypeParameter: PsiTypeParameter) extends ValueType with NamedType {
  private var hash: Int = -1

  override def hashCode: Int = {
    if (hash == -1) {
      hash = Seq(name, arguments, lowerType, upperType, psiTypeParameter).map(_.hashCode()).foldLeft(0)((a, b) => 31 * a + b)
    }
    hash
  }

  def nameAndId = getPsiElementId(psiTypeParameter)

  def isInvariant = psiTypeParameter match {
    case typeParam: ScTypeParam => !typeParam.isCovariant && !typeParam.isContravariant
    case _ => false
  }

  override def equivInner(`type`: ScType, substitutor: ScUndefinedSubstitutor, falseUndef: Boolean)
                         (implicit typeSystem: TypeSystem) =
    (`type` match {
      case that: TypeParameterType => that.psiTypeParameter eq psiTypeParameter
      case _ => false
    }, substitutor)

  override def visitType(visitor: TypeVisitor) = visitor.visitTypeParameterType(this)
}

object TypeParameterType {
  def apply(typeParameter: PsiTypeParameter, substitutor: ScSubstitutor = ScSubstitutor.empty): TypeParameterType = {
    def lift(`type`: ScType) = new Suspension[ScType](substitutor.subst(`type`))

    val (typeParameters, lower, upper) = typeParameter match {
      case typeParam: ScTypeParam =>
        (typeParam.typeParameters,
          typeParam.lowerBound.getOrNothing,
          typeParam.upperBound.getOrAny)
      case _ =>
        val manager = ScalaPsiManager.instance(typeParameter.getProject)
        (typeParameter.getTypeParameters.toSeq,
          manager.psiTypeParameterLowerType(typeParameter),
          manager.psiTypeParameterUpperType(typeParameter))
    }

    TypeParameterType(typeParameter.name,
      typeParameters.map(TypeParameterType(_, substitutor)),
      lift(lower),
      lift(upper),
      typeParameter)
  }
}
