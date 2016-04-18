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
class TypeParameter(val name: String, val typeParams: Seq[TypeParameter], val lowerType: () => ScType,
                    val upperType: () => ScType, val ptp: PsiTypeParameter) {
  def this(ptp: PsiTypeParameter) {
    this(ptp match {
      case tp: ScTypeParam => tp.name
      case _ => ptp.getName
    }, ptp match {
      case tp: ScTypeParam => tp.typeParameters.map(new TypeParameter(_))
      case _ => Seq.empty
    }, ptp match {
      case tp: ScTypeParam => () => tp.lowerBound.getOrNothing
      case _ => () => Nothing //todo: lower type?
    }, ptp match {
      case tp: ScTypeParam => () => tp.upperBound.getOrAny
      case _ => () => Any //todo: upper type?
    }, ptp)
  }

  def nameAndId = getPsiElementId(ptp)

  def update(fun: ScType => ScType): TypeParameter = {
    new TypeParameter(name, typeParams.map(_.update(fun)), {
      val res = fun(lowerType())
      () => res
    }, {
      val res = fun(upperType())
      () => res
    }, ptp)
  }

  def canEqual(other: Any): Boolean = other.isInstanceOf[TypeParameter]

  override def equals(other: Any): Boolean = other match {
    case that: TypeParameter =>
      (that canEqual this) &&
        name == that.name &&
        typeParams == that.typeParams &&
        lowerType() == that.lowerType() &&
        upperType() == that.upperType() &&
        ptp == that.ptp
    case _ => false
  }

  override def hashCode(): Int = {
    val state = Seq(name, typeParams, ptp)
    state.map(_.hashCode()).foldLeft(0)((a, b) => 31 * a + b)
  }
}

object TypeParameter {
  def apply(name: String, typeParams: Seq[TypeParameter], lowerType: () => ScType, upperType: () => ScType,
            ptp: PsiTypeParameter): TypeParameter = {
    new TypeParameter(name, typeParams, lowerType, upperType, ptp)
  }

  def unapply(t: TypeParameter): Option[(String, Seq[TypeParameter], () => ScType, () => ScType, PsiTypeParameter)] = {
    Some(t.name, t.typeParams, t.lowerType, t.upperType, t.ptp)
  }

  def fromArray(ptps: Array[PsiTypeParameter]): Array[TypeParameter] = {
    if (ptps.length == 0) EMPTY_ARRAY
    else ptps.map(new TypeParameter(_))
  }

  val EMPTY_ARRAY: Array[TypeParameter] = Array.empty
}

case class TypeParameterType(name: String, arguments: Seq[TypeParameterType],
                             lower: Suspension[ScType], upper: Suspension[ScType],
                             typeParameter: PsiTypeParameter) extends ValueType with NamedType {
  private var hash: Int = -1

  override def hashCode: Int = {
    if (hash == -1) {
      hash = (((typeParameter.hashCode() * 31 + upper.hashCode) * 31 + lower.hashCode()) * 31 + arguments.hashCode()) * 31 + name.hashCode
    }
    hash
  }

  def nameAndId = getPsiElementId(typeParameter)

  def isInvariant = typeParameter match {
    case typeParam: ScTypeParam => !typeParam.isCovariant && !typeParam.isContravariant
    case _ => false
  }

  override def equivInner(`type`: ScType, substitutor: ScUndefinedSubstitutor, falseUndef: Boolean)
                         (implicit typeSystem: TypeSystem): (Boolean, ScUndefinedSubstitutor) = {
    (`type` match {
      case that: TypeParameterType => that.typeParameter eq typeParameter
      case _ => false
    }, substitutor)
  }

  override def visitType(visitor: TypeVisitor) = visitor.visitTypeParameterType(this)
}

object TypeParameterType {
  def apply(typeParameter: PsiTypeParameter, substitutor: ScSubstitutor = ScSubstitutor.empty): TypeParameterType = {
    def lift(function: () => ScType) = new Suspension[ScType](function)

    val (name, typeParameters, lower, upper) = typeParameter match {
      case tp: ScTypeParam =>
        (tp.name,
          tp.typeParameters,
          lift(() => substitutor.subst(tp.lowerBound.getOrNothing)),
          lift(() => substitutor.subst(tp.upperBound.getOrAny))
          )
      case _ =>
        val manager = ScalaPsiManager.instance(typeParameter.getProject)
        (typeParameter.name,
          typeParameter.getTypeParameters.toSeq,
          lift(() => substitutor.subst(manager.psiTypeParameterLowerType(typeParameter))),
          lift(() => substitutor.subst(manager.psiTypeParameterUpperType(typeParameter)))
          )
    }

    TypeParameterType(name, typeParameters.map(TypeParameterType(_, substitutor)), lower, upper, typeParameter)
  }
}
