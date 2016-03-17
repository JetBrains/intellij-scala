package org.jetbrains.plugins.scala
package lang.psi.types.nonvalue

import com.intellij.openapi.project.Project
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.{PsiElement, PsiParameter, PsiTypeParameter}
import org.jetbrains.plugins.scala.extensions.PsiParameterExt
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiUtil
import org.jetbrains.plugins.scala.lang.psi.api.statements.params.{ScParameter, ScTypeParam}
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiManager
import org.jetbrains.plugins.scala.lang.psi.types._
import org.jetbrains.plugins.scala.lang.psi.types.api.TypeSystem
import org.jetbrains.plugins.scala.lang.psi.types.result.TypingContext

import scala.collection.immutable.{HashMap, HashSet}

/**
 * @author ilyas
 */

/**
 * This is internal type, no expression can have such type.
 */
trait NonValueType extends ScType {
  def isValue = false
}

/**
 * Generalized parameter. It's not psi element. So can be used in any place.
 * Some difference
 */
case class Parameter(name: String, deprecatedName: Option[String], paramType: ScType, expectedType: ScType,
                     isDefault: Boolean, isRepeated: Boolean, isByName: Boolean,
                     index: Int = -1, psiParam: Option[PsiParameter] = None, defaultType: Option[ScType] = None) {

  def this(name: String, deprecatedName: Option[String], paramType: ScType,
           isDefault: Boolean, isRepeated: Boolean, isByName: Boolean, index: Int) {
    this(name, deprecatedName, paramType, paramType, isDefault, isRepeated, isByName, index)
  }

  def this(param: ScParameter) {
    this(param.name, param.deprecatedName, param.getType(TypingContext.empty).getOrNothing, param.getType(TypingContext.empty).getOrNothing,
      param.isDefaultParam, param.isRepeatedParameter, param.isCallByNameParameter, param.index, Some(param), param.getDefaultExpression.flatMap(_.getType().toOption))
  }

  def this(param: PsiParameter) {
    this(param.getName, None, param.paramType, param.paramType, false, param.isVarArgs, false, param.index, Some(param))
  }

  def paramInCode: Option[ScParameter] = psiParam match {
    case Some(scParam: ScParameter) => Some(scParam)
    case _ => None
  }

  def nameInCode = psiParam.map(_.getName)

}


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

case class ScMethodType(returnType: ScType, params: Seq[Parameter], isImplicit: Boolean)
                       (val project: Project, val scope: GlobalSearchScope) extends NonValueType {

  def visitType(visitor: ScalaTypeVisitor) {
    visitor.visitMethodType(this)
  }

  override def typeDepth: Int = returnType.typeDepth

  def inferValueType: ValueType = {
    ScFunctionType(returnType.inferValueType, params.map(p => {
      val inferredParamType = p.paramType.inferValueType
      if (!p.isRepeated) inferredParamType
      else {
        val seqClass = ScalaPsiManager.instance(project).getCachedClass(scope, "scala.collection.Seq")
        seqClass.fold(inferredParamType) { inferred =>
            ScParameterizedType(ScDesignatorType(inferred), Seq(inferredParamType))
        }
      }
    }))(project, scope)
  }

  override def removeAbstracts = new ScMethodType(returnType.removeAbstracts,
    params.map(p => p.copy(paramType = p.paramType.removeAbstracts)), isImplicit)(project, scope)

  override def recursiveUpdate(update: ScType => (Boolean, ScType), visited: HashSet[ScType]): ScType = {
    if (visited.contains(this)) {
      return update(this) match {
        case (true, res) => res
        case _ => this
      }
    }
    val newVisited = visited + this
    update(this) match {
      case (true, res) => res
      case _ =>
        new ScMethodType(returnType.recursiveUpdate(update, newVisited),
          params.map(p => p.copy(paramType = p.paramType.recursiveUpdate(update, newVisited))), isImplicit)(project, scope)
    }
  }

  override def recursiveVarianceUpdateModifiable[T](data: T, update: (ScType, Int, T) => (Boolean, ScType, T),
                                           variance: Int = 1): ScType = {
    update(this, variance, data) match {
      case (true, res, _) => res
      case (_, _, newData) =>
        new ScMethodType(returnType.recursiveVarianceUpdateModifiable(newData, update, variance),
          params.map(p => p.copy(paramType = p.paramType.recursiveVarianceUpdateModifiable(newData, update, -variance))),
          isImplicit)(project, scope)
    }
  }

  override def equivInner(r: ScType, uSubst: ScUndefinedSubstitutor, falseUndef: Boolean)
                         (implicit typeSystem: TypeSystem): (Boolean, ScUndefinedSubstitutor) = {
    var undefinedSubst = uSubst
    r match {
      case m: ScMethodType =>
        if (m.params.length != params.length) return (false, undefinedSubst)
        var t = m.returnType.equiv(returnType, undefinedSubst, falseUndef)
        if (!t._1) return (false, undefinedSubst)
        undefinedSubst = t._2
        var i = 0
        while (i < params.length) {
          //todo: Seq[Type] instead of Type*
          if (params(i).isRepeated != m.params(i).isRepeated) return (false, undefinedSubst)
          t = params(i).paramType.equiv(m.params(i).paramType, undefinedSubst, falseUndef)
          if (!t._1) return (false, undefinedSubst)
          undefinedSubst = t._2
          i = i + 1
        }
        (true, undefinedSubst)
      case _ => (false, undefinedSubst)
    }
  }
}

case class ScTypePolymorphicType(internalType: ScType, typeParameters: Seq[TypeParameter]) extends NonValueType {
  if (internalType.isInstanceOf[ScTypePolymorphicType]) {
    throw new IllegalArgumentException("Polymorphic type can't have wrong internal type")
  }


  def polymorphicTypeSubstitutor: ScSubstitutor = polymorphicTypeSubstitutor(inferValueType = false)

  def polymorphicTypeSubstitutor(inferValueType: Boolean): ScSubstitutor =
    new ScSubstitutor(new HashMap[(String, PsiElement), ScType] ++ typeParameters.map(tp => {
      var contraVariant = 0
      var coOrInVariant = 0
      internalType.recursiveVarianceUpdate {
        case (typez: ScType, i: Int) =>
          val pair = typez match {
            case tp: ScTypeParameterType => (tp.name, ScalaPsiUtil.getPsiElementId(tp.param))
            case ScUndefinedType(tp) => (tp.name, ScalaPsiUtil.getPsiElementId(tp.param))
            case ScAbstractType(tp, _, _) => (tp.name, ScalaPsiUtil.getPsiElementId(tp.param))
            case _ => null
          }
          if (pair != null) {
            val (tpName, id) = pair
            if (tp.name == tpName && id == ScalaPsiUtil.getPsiElementId(tp.ptp)) {
              if (i == -1) contraVariant += 1
              else coOrInVariant += 1
            }
          }
          (false, typez)
      }
      if (coOrInVariant == 0 && contraVariant != 0)
        ((tp.name, ScalaPsiUtil.getPsiElementId(tp.ptp)), tp.upperType().inferValueType)
      else
        ((tp.name, ScalaPsiUtil.getPsiElementId(tp.ptp)), tp.lowerType().inferValueType)
    }), Map.empty, None)

  def abstractTypeSubstitutor: ScSubstitutor = {
    def hasRecursiveTypeParameters(typez: ScType): Boolean = {
      var hasRecursiveTypeParameters = false
      typez.recursiveUpdate {
        case tpt: ScTypeParameterType =>
          typeParameters.find(tp => (tp.name, ScalaPsiUtil.getPsiElementId(tp.ptp)) == (tpt.name, tpt.getId)) match {
            case None => (true, tpt)
            case _ =>
              hasRecursiveTypeParameters = true
              (true, tpt)
          }
        case tp: ScType => (hasRecursiveTypeParameters, tp)
      }
      hasRecursiveTypeParameters
    }
    new ScSubstitutor(new HashMap[(String, PsiElement), ScType] ++ typeParameters.map(tp => {
      val lowerType: ScType = if (hasRecursiveTypeParameters(tp.lowerType())) Nothing else tp.lowerType()
      val upperType: ScType = if (hasRecursiveTypeParameters(tp.upperType())) Any else tp.upperType()
      ((tp.name, ScalaPsiUtil.getPsiElementId(tp.ptp)),
              new ScAbstractType(new ScTypeParameterType(tp.ptp, ScSubstitutor.empty), lowerType, upperType))
    }), Map.empty, None)
  }

  def abstractOrLowerTypeSubstitutor(implicit typeSystem: TypeSystem): ScSubstitutor = {
    def hasRecursiveTypeParameters(typez: ScType): Boolean = {
      var hasRecursiveTypeParameters = false
      typez.recursiveUpdate {
        case tpt: ScTypeParameterType =>
          typeParameters.find(tp => (tp.name, ScalaPsiUtil.getPsiElementId(tp.ptp)) == (tpt.name, tpt.getId)) match {
            case None => (true, tpt)
            case _ =>
              hasRecursiveTypeParameters = true
              (true, tpt)
          }
        case tp: ScType => (hasRecursiveTypeParameters, tp)
      }
      hasRecursiveTypeParameters
    }
    new ScSubstitutor(new HashMap[(String, PsiElement), ScType] ++ typeParameters.map(tp => {
      val lowerType: ScType = if (hasRecursiveTypeParameters(tp.lowerType())) Nothing else tp.lowerType()
      val upperType: ScType = if (hasRecursiveTypeParameters(tp.upperType())) Any else tp.upperType()
      ((tp.name, ScalaPsiUtil.getPsiElementId(tp.ptp)),
        if (lowerType.equiv(Nothing)) new ScAbstractType(new ScTypeParameterType(tp.ptp, ScSubstitutor.empty), lowerType, upperType)
        else lowerType)
    }), Map.empty, None)
  }

  def typeParameterTypeSubstitutor: ScSubstitutor =
    new ScSubstitutor(new HashMap[(String, PsiElement), ScType] ++ typeParameters.map { tp =>
      ((tp.name, ScalaPsiUtil.getPsiElementId(tp.ptp)), new ScTypeParameterType(tp.ptp, ScSubstitutor.empty))
    }, Map.empty, None)

  def inferValueType: ValueType = {
    polymorphicTypeSubstitutor(inferValueType = true).subst(internalType.inferValueType).asInstanceOf[ValueType]
  }

  override def removeAbstracts = ScTypePolymorphicType(internalType.removeAbstracts, typeParameters.map(tp => {
    TypeParameter(tp.name, tp.typeParams /* todo: ? */, () => tp.lowerType().removeAbstracts,
      () => tp.upperType().removeAbstracts, tp.ptp)
  }))

  override def recursiveUpdate(update: ScType => (Boolean, ScType), visited: HashSet[ScType]): ScType = {
    if (visited.contains(this)) {
      return update(this) match {
        case (true, res) => res
        case _ => this
      }
    }
    val newVisited = visited + this
    update(this) match {
      case (true, res) => res
      case _ =>
        ScTypePolymorphicType(internalType.recursiveUpdate(update, newVisited), typeParameters.map(tp => {
          TypeParameter(tp.name, tp.typeParams /* todo: ? */, {
            val res = tp.lowerType().recursiveUpdate(update, newVisited)
            () => res
          }, {
            val res = tp.upperType().recursiveUpdate(update, newVisited)
            () => res
          }, tp.ptp)
        }))
    }
  }

  override def recursiveVarianceUpdateModifiable[T](data: T, update: (ScType, Int, T) => (Boolean, ScType, T),
                                           variance: Int = 1): ScType = {
    update(this, variance, data) match {
      case (true, res, _) => res
      case (_, _, newData) =>
        ScTypePolymorphicType(internalType.recursiveVarianceUpdateModifiable(newData, update, variance), typeParameters.map(tp => {
          TypeParameter(tp.name, tp.typeParams /* todo: ? */,
            () => tp.lowerType().recursiveVarianceUpdateModifiable(newData, update, -variance),
            () => tp.upperType().recursiveVarianceUpdateModifiable(newData, update, variance), tp.ptp)
        }))
    }
  }

  override def equivInner(r: ScType, uSubst: ScUndefinedSubstitutor, falseUndef: Boolean)
                         (implicit typeSystem: TypeSystem): (Boolean, ScUndefinedSubstitutor) = {
    var undefinedSubst = uSubst
    r match {
      case p: ScTypePolymorphicType =>
        if (typeParameters.length != p.typeParameters.length) return (false, undefinedSubst)
        var i = 0
        while (i < typeParameters.length) {
          var t = typeParameters(i).lowerType().equiv(p.typeParameters(i).lowerType(), undefinedSubst, falseUndef)
          if (!t._1) return (false,undefinedSubst)
          undefinedSubst = t._2
          t = typeParameters(i).upperType().equiv(p.typeParameters(i).upperType(), undefinedSubst, falseUndef)
          if (!t._1) return (false, undefinedSubst)
          undefinedSubst = t._2
          i = i + 1
        }
        val subst = new ScSubstitutor(new collection.immutable.HashMap[(String, PsiElement), ScType] ++
                typeParameters.zip(p.typeParameters).map({
          tuple => ((tuple._1.name, ScalaPsiUtil.getPsiElementId(tuple._1.ptp)), new ScTypeParameterType(tuple._2.name,
            tuple._2.ptp match {
              case p: ScTypeParam => p.typeParameters.toList.map{new ScTypeParameterType(_, ScSubstitutor.empty)}
              case _ => Nil
            }, new Suspension(tuple._2.lowerType), new Suspension(tuple._2.upperType), tuple._2.ptp))
        }), Map.empty, None)
        subst.subst(internalType).equiv(p.internalType, undefinedSubst, falseUndef)
      case _ => (false, undefinedSubst)
    }
  }

  def visitType(visitor: ScalaTypeVisitor): Unit = {
    visitor.visitTypePolymorphicType(this)
  }

  override def typeDepth: Int = {
    if (typeParameters.nonEmpty) internalType.typeDepth.max(ScType.typeParamsDepth(typeParameters.toArray) + 1)
    else internalType.typeDepth
  }
}
