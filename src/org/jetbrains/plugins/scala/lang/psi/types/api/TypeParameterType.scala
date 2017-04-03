package org.jetbrains.plugins.scala.lang.psi.types.api

import java.util.Objects

import com.intellij.psi.PsiTypeParameter
import org.jetbrains.plugins.scala.extensions.PsiNamedElementExt
import org.jetbrains.plugins.scala.lang.psi.api.statements.params.{PsiTypeParameterExt, ScTypeParam}
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiManager
import org.jetbrains.plugins.scala.lang.psi.types.api.TypeParameter.javaPsiTypeParameterUpperType
import org.jetbrains.plugins.scala.lang.psi.types.{NamedType, ScSubstitutor, ScType, ScUndefinedSubstitutor}

import scala.collection.Seq

/**
  * @author ven
  */
sealed trait Suspension {
  def v: ScType
}

object Suspension {
  private class Lazy(private var fun: () => ScType) extends Suspension {
    def this(`type`: ScType) = this({ () => `type` })

    lazy val v: ScType = {
      val res = fun()
      fun = null
      res
    }
  }
  private class Strict(val v: ScType) extends Suspension

  def apply(fun: () => ScType): Suspension = new Lazy(fun)
  def apply(v: ScType): Suspension = new Strict(v)
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

  def name: String = psiTypeParameter.name

  def nameAndId: (String, Long) = psiTypeParameter.nameAndId

  def update(function: ScType => ScType): TypeParameter = TypeParameter(
    typeParameters.map(_.update(function)),
    Suspension(function(lowerType.v)),
    Suspension(function(upperType.v)),
    psiTypeParameter)

  def updateWithVariance(function: (ScType, Int) => ScType, variance: Int): TypeParameter = TypeParameter(
    typeParameters.map(_.updateWithVariance(function, variance)),
    Suspension(function(lowerType.v, variance)),
    Suspension(function(upperType.v, -variance)),
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

  override def hashCode(): Int = Objects.hash(name, typeParameters, psiTypeParameter)
}

object TypeParameter {
  def apply(typeParameter: PsiTypeParameter): TypeParameter = {
    val (typeParameters, lazyLower, lazyUpper) = typeParameter match {
      case typeParam: ScTypeParam =>
        (typeParam.typeParameters,
          () => typeParam.lowerBound.getOrNothing,
          () => typeParam.upperBound.getOrAny)
      case _ =>
        (Seq.empty,
          () => Nothing,
          () => javaPsiTypeParameterUpperType(typeParameter)
        )
    }
    TypeParameter(
      typeParameters.map(TypeParameter(_)),
      Suspension(lazyLower),
      Suspension(lazyUpper),
      typeParameter)
  }

  def javaPsiTypeParameterUpperType(typeParameter: PsiTypeParameter): ScType = {
    val manager = ScalaPsiManager.instance(typeParameter.getProject)
    manager.javaPsiTypeParameterUpperType(typeParameter)
  }
}

case class TypeParameterType(arguments: Seq[TypeParameterType],
                             lowerType: Suspension,
                             upperType: Suspension,
                             psiTypeParameter: PsiTypeParameter) extends ValueType with NamedType {

  override val name: String = psiTypeParameter.name

  def nameAndId: (String, Long) = psiTypeParameter.nameAndId

  private var hash: Int = -1

  //noinspection HashCodeUsesVar
  override def hashCode: Int = {
    if (hash == -1)
      hash = Objects.hash(name, arguments, lowerType, upperType, psiTypeParameter)

    hash
  }

  def isInvariant: Boolean = psiTypeParameter match {
    case typeParam: ScTypeParam => !typeParam.isCovariant && !typeParam.isContravariant
    case _ => false
  }

  def isCovariant: Boolean = psiTypeParameter match {
    case typeParam: ScTypeParam => typeParam.isCovariant
    case _ => false
  }

  def isContravariant: Boolean = psiTypeParameter match {
    case typeParam: ScTypeParam => typeParam.isContravariant
    case _ => false
  }

  override def equivInner(`type`: ScType, substitutor: ScUndefinedSubstitutor, falseUndef: Boolean)
                         (implicit typeSystem: TypeSystem): (Boolean, ScUndefinedSubstitutor) =
    (`type` match {
      case that: TypeParameterType => (that.psiTypeParameter eq psiTypeParameter) || {
        (psiTypeParameter, that.psiTypeParameter) match {
          case (myBound: ScTypeParam, thatBound: ScTypeParam) =>
            //TODO this is a temporary hack, so ignore substitutor for now
            myBound.lowerBound.exists(typeSystem.equivalence.equiv(_, thatBound.lowerBound.getOrNothing)) &&
              myBound.upperBound.exists(typeSystem.equivalence.equiv(_, thatBound.upperBound.getOrNothing)) &&
              (myBound.name == thatBound.name || thatBound.isHigherKindedTypeParameter || myBound.isHigherKindedTypeParameter)
          case _ => false
        }
      }
      case _ => false
    }, substitutor)

  override def visitType(visitor: TypeVisitor): Unit = visitor.visitTypeParameterType(this)
}

object TypeParameterType {
  def apply(typeParameter: PsiTypeParameter, maybeSubstitutor: Option[ScSubstitutor] = Some(ScSubstitutor.empty)): TypeParameterType = {
    def lift(function: () => ScType) = Suspension(maybeSubstitutor match {
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
        (maybeSubstitutor match {
          case Some(_) => typeParameter.getTypeParameters.toSeq
          case _ => Seq.empty
        },
          () => Nothing,
          () => javaPsiTypeParameterUpperType(typeParameter)
        )
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
