package org.jetbrains.plugins.scala.lang.psi.types.api

import com.intellij.psi.PsiTypeParameter
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiUtil.getPsiElementId
import org.jetbrains.plugins.scala.lang.psi.api.statements.params.ScTypeParam
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiManager
import org.jetbrains.plugins.scala.lang.psi.types.{NamedType, ScSubstitutor, ScType, ScUndefinedSubstitutor}

import scala.collection.Seq

/**
  * @author ven
  */
class Suspension(fun: () => ScType) {
  def this(`type`: ScType) = this({ () => `type` })

  lazy val v = fun()
}

/**
  * Class representing type parameters in our type system. Can be constructed from psi.
  * todo: lower and upper types will be reevaluated many times, is it good or bad? Seems bad. What other ways to fix SCL-7216?
  *
  * @param lowerType important to be lazy, see SCL-7216
  * @param upperType important to be lazy, see SCL-7216
  */
case class TypeParameter(typeParameters: Seq[TypeParameter],
                         lowerType: Suspension,
                         upperType: Suspension,
                         psiTypeParameter: PsiTypeParameter) {
  val nameAndId = getPsiElementId(psiTypeParameter)

  val name = nameAndId._1

  def update(function: ScType => ScType): TypeParameter = TypeParameter(
    typeParameters.map(_.update(function)),
    new Suspension(function(lowerType.v)),
    new Suspension(function(upperType.v)),
    psiTypeParameter)

  def updateWithVariance(function: (ScType, Int) => ScType, variance: Int): TypeParameter = TypeParameter(
    typeParameters.map(_.updateWithVariance(function, variance)),
    new Suspension(function(lowerType.v, variance)),
    new Suspension(function(upperType.v, -variance)),
    psiTypeParameter)

  def canEqual(other: Any): Boolean = other.isInstanceOf[TypeParameter]

  override def equals(other: Any): Boolean = other match {
    case that: TypeParameter =>
      (that canEqual this) &&
        name == that.name &&
        typeParameters == that.typeParameters &&
        lowerType.v == that.lowerType.v &&
        upperType.v == that.upperType.v &&
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
    TypeParameter(
      typeParameters.map(TypeParameter(_)),
      new Suspension(maybeLower.getOrElse(Nothing)),
      new Suspension(maybeUpper.getOrElse(Any)),
      typeParameter)
  }
}

case class TypeParameterType(arguments: Seq[TypeParameterType],
                             lowerType: Suspension,
                             upperType: Suspension,
                             psiTypeParameter: PsiTypeParameter) extends ValueType with NamedType {
  val nameAndId = getPsiElementId(psiTypeParameter)

  override val name = nameAndId._1

  private var hash: Int = -1

  override def hashCode: Int = {
    if (hash == -1) {
      hash = Seq(name, arguments, lowerType, upperType, psiTypeParameter).map(_.hashCode()).foldLeft(0)((a, b) => 31 * a + b)
    }
    hash
  }

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
  def apply(typeParameter: PsiTypeParameter, maybeSubstitutor: Option[ScSubstitutor] = Some(ScSubstitutor.empty)): TypeParameterType = {
    def lift(function: () => ScType) = new Suspension(maybeSubstitutor match {
      case Some(substitutor) => () => substitutor.subst(function())
      case _ => function
    })

    val (arguments, lower, upper) = typeParameter match {
      case typeParam: ScTypeParam =>
        // todo rework for error handling!
        (typeParam.typeParameters,
          () => typeParam.lowerBound.getOrNothing,
          () => typeParam.upperBound.getOrAny)
      case _ =>
        val manager = ScalaPsiManager.instance(typeParameter.getProject)
        (maybeSubstitutor match {
          case Some(_) => typeParameter.getTypeParameters.toSeq
          case _ => Seq.empty
        },
          () => manager.psiTypeParameterLowerType(typeParameter),
          () => manager.psiTypeParameterUpperType(typeParameter))
    }
    TypeParameterType(
      arguments.map(TypeParameterType(_, maybeSubstitutor)),
      lift(lower),
      lift(upper),
      typeParameter)
  }

  def apply(typeParameter: TypeParameter): TypeParameterType = {
    val TypeParameter(typeParameters, lowerType, upperType, psiTypeParameter) = typeParameter
    TypeParameterType(
      typeParameters.map(TypeParameterType(_)),
      lowerType,
      upperType,
      psiTypeParameter
    )
  }
}
