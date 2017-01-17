package org.jetbrains.plugins.scala
package lang.psi.types.nonvalue

import com.intellij.psi.PsiParameter
import org.jetbrains.plugins.scala.extensions.PsiParameterExt
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiElement.ElementScope
import org.jetbrains.plugins.scala.lang.psi.api.statements.params.{ScParameter, ScTypeParam}
import org.jetbrains.plugins.scala.lang.psi.types._
import org.jetbrains.plugins.scala.lang.psi.types.api._
import org.jetbrains.plugins.scala.lang.psi.types.api.designator.ScDesignatorType
import org.jetbrains.plugins.scala.lang.psi.types.result.TypingContext

import scala.collection.immutable.HashSet

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
case class Parameter(name: String, deprecatedName: Option[String],
                     paramType: ScType,
                     expectedType: ScType,
                     isDefault: Boolean,
                     isRepeated: Boolean,
                     isByName: Boolean,
                     index: Int = -1,
                     psiParam: Option[PsiParameter] = None,
                     defaultType: Option[ScType] = None) {
  def paramInCode: Option[ScParameter] = psiParam.collect {
    case parameter: ScParameter => parameter
  }

  def nameInCode: Option[String] = psiParam.map(_.getName)
}

object Parameter {
  def apply(paramType: ScType,
            isRepeated: Boolean,
            index: Int): Parameter =
    new Parameter(name = "",
      deprecatedName = None,
      paramType = paramType,
      expectedType = paramType,
      isDefault = false,
      isRepeated = isRepeated,
      isByName = false,
      index = index)

  def apply(parameter: PsiParameter): Parameter = parameter match {
    case scParameter: ScParameter =>
      val `type` = scParameter.getType(TypingContext.empty).getOrNothing

      new Parameter(name = scParameter.name,
        deprecatedName = scParameter.deprecatedName,
        paramType = `type`,
        expectedType = `type`,
        isDefault = scParameter.isDefaultParam,
        isRepeated = scParameter.isRepeatedParameter,
        isByName = scParameter.isCallByNameParameter,
        index = scParameter.index,
        psiParam = Some(scParameter),
        defaultType = scParameter.getDefaultExpression.flatMap(_.getType().toOption))
    case _ =>
      val `type` = parameter.paramType(exact = false)

      new Parameter(name = parameter.getName,
        deprecatedName = None,
        paramType = `type`,
        expectedType = `type`,
        isDefault = false,
        isRepeated = parameter.isVarArgs,
        isByName = false,
        index = parameter.index,
        psiParam = Some(parameter))

  }
}

case class ScMethodType(returnType: ScType, params: Seq[Parameter], isImplicit: Boolean)
                       (implicit val elementScope: ElementScope) extends NonValueType with TypeInTypeSystem {
  implicit val typeSystem = elementScope.typeSystem

  override def visitType(visitor: TypeVisitor): Unit = visitor.visitMethodType(this)

  override def typeDepth: Int = returnType.typeDepth

  def inferValueType: ValueType = {
    FunctionType(returnType.inferValueType, params.map(p => {
      val inferredParamType = p.paramType.inferValueType
      if (!p.isRepeated) inferredParamType
      else {
        val seqClass = elementScope.getCachedClass("scala.collection.Seq")
        seqClass.fold(inferredParamType) { inferred =>
            ScParameterizedType(ScDesignatorType(inferred), Seq(inferredParamType))
        }
      }
    }))
  }

  override def removeAbstracts = ScMethodType(returnType.removeAbstracts,
    params.map(p => p.copy(paramType = p.paramType.removeAbstracts)),
    isImplicit)

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
        ScMethodType(returnType.recursiveUpdate(update, newVisited),
          params.map(p => p.copy(paramType = p.paramType.recursiveUpdate(update, newVisited))),
          isImplicit)
    }
  }

  override def recursiveVarianceUpdateModifiable[T](data: T, update: (ScType, Int, T) => (Boolean, ScType, T),
                                           variance: Int = 1): ScType = {
    update(this, variance, data) match {
      case (true, res, _) => res
      case (_, _, newData) =>
        ScMethodType(returnType.recursiveVarianceUpdateModifiable(newData, update, variance),
          params.map(p => p.copy(paramType = p.paramType.recursiveVarianceUpdateModifiable(newData, update, -variance))),
          isImplicit)
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

case class ScTypePolymorphicType(internalType: ScType, typeParameters: Seq[TypeParameter])
                                (implicit val typeSystem: TypeSystem) extends NonValueType with TypeInTypeSystem {
  if (internalType.isInstanceOf[ScTypePolymorphicType]) {
    throw new IllegalArgumentException("Polymorphic type can't have wrong internal type")
  }

  def polymorphicTypeSubstitutor: ScSubstitutor = polymorphicTypeSubstitutor(inferValueType = false)

  def polymorphicTypeSubstitutor(inferValueType: Boolean): ScSubstitutor =
    ScSubstitutor(typeParameters.map(tp => {
      var contraVariant = 0
      var coOrInVariant = 0
      internalType.recursiveVarianceUpdate {
        case (typez: ScType, i: Int) =>
          val pair = typez match {
            case tp: TypeParameterType => tp.nameAndId
            case UndefinedType(tp, _) => tp.nameAndId
            case ScAbstractType(tp, _, _) => tp.nameAndId
            case _ => null
          }
          if (pair != null) {
            if (tp.nameAndId == pair) {
              if (i == -1) contraVariant += 1
              else coOrInVariant += 1
            }
          }
          (false, typez)
      }
      if (coOrInVariant == 0 && contraVariant != 0)
        (tp.nameAndId, tp.upperType.v.inferValueType)
      else
        (tp.nameAndId, tp.lowerType.v.inferValueType)
    }).toMap)

  def abstractTypeSubstitutor: ScSubstitutor = {
    def hasRecursiveTypeParameters(typez: ScType): Boolean = {
      var hasRecursiveTypeParameters = false
      typez.recursiveUpdate {
        case tpt: TypeParameterType =>
          typeParameters.find(_.nameAndId == tpt.nameAndId) match {
            case None => (true, tpt)
            case _ =>
              hasRecursiveTypeParameters = true
              (true, tpt)
          }
        case tp: ScType => (hasRecursiveTypeParameters, tp)
      }
      hasRecursiveTypeParameters
    }
    ScSubstitutor(typeParameters.map(tp => {
      val lowerType: ScType = if (hasRecursiveTypeParameters(tp.lowerType.v)) Nothing else tp.lowerType.v
      val upperType: ScType = if (hasRecursiveTypeParameters(tp.upperType.v)) Any else tp.upperType.v
      (tp.nameAndId, ScAbstractType(TypeParameterType(tp.psiTypeParameter), lowerType, upperType))
    }).toMap)
  }

  def abstractOrLowerTypeSubstitutor(implicit typeSystem: TypeSystem): ScSubstitutor = {
    def hasRecursiveTypeParameters(typez: ScType): Boolean = {
      var hasRecursiveTypeParameters = false
      typez.recursiveUpdate {
        case tpt: TypeParameterType =>
          typeParameters.find(_.nameAndId == tpt.nameAndId) match {
            case None => (true, tpt)
            case _ =>
              hasRecursiveTypeParameters = true
              (true, tpt)
          }
        case tp: ScType => (hasRecursiveTypeParameters, tp)
      }
      hasRecursiveTypeParameters
    }
    ScSubstitutor(typeParameters.map(tp => {
      val lowerType: ScType = if (hasRecursiveTypeParameters(tp.lowerType.v)) Nothing else tp.lowerType.v
      val upperType: ScType = if (hasRecursiveTypeParameters(tp.upperType.v)) Any else tp.upperType.v
      (tp.nameAndId,
        if (lowerType.equiv(Nothing)) ScAbstractType(TypeParameterType(tp.psiTypeParameter), lowerType, upperType)
        else lowerType)
    }).toMap)
  }

  def typeParameterTypeSubstitutor: ScSubstitutor =
    ScSubstitutor(typeParameters.map { tp =>
      (tp.nameAndId, TypeParameterType(tp.psiTypeParameter))
    }.toMap)

  def inferValueType: ValueType = {
    polymorphicTypeSubstitutor(inferValueType = true).subst(internalType.inferValueType).asInstanceOf[ValueType]
  }

  override def removeAbstracts = ScTypePolymorphicType(internalType.removeAbstracts,
    typeParameters.map {
      case TypeParameter(parameters, lowerType, upperType, psiTypeParameter) =>
        TypeParameter(parameters, // todo: ?
          Suspension(lowerType.v.removeAbstracts),
          Suspension(upperType.v.removeAbstracts),
          psiTypeParameter)
    })

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
        def innerUpdate(`type`: ScType) =
          `type`.recursiveUpdate(update, newVisited)

        ScTypePolymorphicType(innerUpdate(internalType),
          typeParameters.map {
            case TypeParameter(parameters, lowerType, upperType, psiTypeParameter) =>
              TypeParameter(parameters, // TODO: ?
                Suspension(innerUpdate(lowerType.v)),
                Suspension(innerUpdate(upperType.v)),
                psiTypeParameter)
          })
    }
  }

  override def recursiveVarianceUpdateModifiable[T](data: T, update: (ScType, Int, T) => (Boolean, ScType, T),
                                                    variance: Int = 1): ScType = {
    update(this, variance, data) match {
      case (true, res, _) => res
      case (_, _, newData) =>
        def innerUpdate(`type`: ScType, variance: Int) =
          `type`.recursiveVarianceUpdateModifiable(newData, update, variance)

        ScTypePolymorphicType(innerUpdate(internalType, variance),
          typeParameters.map {
            case TypeParameter(parameters, lowerType, upperType, psiTypeParameter) =>
              TypeParameter(parameters, // TODO: ?
                Suspension(innerUpdate(lowerType.v, -variance)),
                Suspension(innerUpdate(upperType.v, variance)),
                psiTypeParameter)
          })
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
          var t = typeParameters(i).lowerType.v.equiv(p.typeParameters(i).lowerType.v, undefinedSubst, falseUndef)
          if (!t._1) return (false,undefinedSubst)
          undefinedSubst = t._2
          t = typeParameters(i).upperType.v.equiv(p.typeParameters(i).upperType.v, undefinedSubst, falseUndef)
          if (!t._1) return (false, undefinedSubst)
          undefinedSubst = t._2
          i = i + 1
        }
        val subst = ScSubstitutor(
          typeParameters.zip(p.typeParameters).map({
            case (key, TypeParameter(_, lowerType, upperType, psiTypeParameter)) =>
              (key.nameAndId, TypeParameterType(
                (psiTypeParameter match {
                  case typeParam: ScTypeParam => typeParam.typeParameters
                  case _ => Seq.empty
                }).map(TypeParameterType(_)),
                lowerType,
                upperType,
                psiTypeParameter))
        }).toMap)
        subst.subst(internalType).equiv(p.internalType, undefinedSubst, falseUndef)
      case _ => (false, undefinedSubst)
    }
  }

  override def visitType(visitor: TypeVisitor): Unit = visitor match {
    case scalaVisitor: ScalaTypeVisitor => scalaVisitor.visitTypePolymorphicType(this)
    case _ =>
  }

  override def typeDepth: Int = internalType.typeDepth.max(typeParameters.toArray.depth)
}
