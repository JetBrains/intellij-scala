package org.jetbrains.plugins.scala
package lang.psi.types.nonvalue

import com.intellij.openapi.project.Project
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.{PsiElement, PsiParameter}
import org.jetbrains.plugins.scala.extensions.PsiParameterExt
import org.jetbrains.plugins.scala.lang.psi.api.statements.params.{ScParameter, ScTypeParam}
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiManager
import org.jetbrains.plugins.scala.lang.psi.types._
import org.jetbrains.plugins.scala.lang.psi.types.api._
import org.jetbrains.plugins.scala.lang.psi.types.api.designator.ScDesignatorType
import org.jetbrains.plugins.scala.lang.psi.types.result.TypingContext
import org.jetbrains.plugins.scala.project.ProjectExt

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

case class ScMethodType(returnType: ScType, params: Seq[Parameter], isImplicit: Boolean)
                       (val project: Project, val scope: GlobalSearchScope) extends NonValueType with TypeInTypeSystem {
  implicit val typeSystem = project.typeSystem

  override def visitType(visitor: TypeVisitor) = visitor.visitMethodType(this)

  override def typeDepth: Int = returnType.typeDepth

  def inferValueType: ValueType = {
    FunctionType(returnType.inferValueType, params.map(p => {
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

case class ScTypePolymorphicType(internalType: ScType, typeParameters: Seq[TypeParameter])
                                (implicit val typeSystem: TypeSystem) extends NonValueType with TypeInTypeSystem {
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
    }), Map.empty, None)

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
    new ScSubstitutor(new HashMap[(String, PsiElement), ScType] ++ typeParameters.map(tp => {
      val lowerType: ScType = if (hasRecursiveTypeParameters(tp.lowerType.v)) Nothing else tp.lowerType.v
      val upperType: ScType = if (hasRecursiveTypeParameters(tp.upperType.v)) Any else tp.upperType.v
      (tp.nameAndId, ScAbstractType(TypeParameterType(tp.psiTypeParameter), lowerType, upperType))
    }), Map.empty, None)
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
    new ScSubstitutor(new HashMap[(String, PsiElement), ScType] ++ typeParameters.map(tp => {
      val lowerType: ScType = if (hasRecursiveTypeParameters(tp.lowerType.v)) Nothing else tp.lowerType.v
      val upperType: ScType = if (hasRecursiveTypeParameters(tp.upperType.v)) Any else tp.upperType.v
      (tp.nameAndId,
        if (lowerType.equiv(Nothing)) ScAbstractType(TypeParameterType(tp.psiTypeParameter), lowerType, upperType)
        else lowerType)
    }), Map.empty, None)
  }

  def typeParameterTypeSubstitutor: ScSubstitutor =
    new ScSubstitutor(new HashMap[(String, PsiElement), ScType] ++ typeParameters.map { tp =>
      (tp.nameAndId, TypeParameterType(tp.psiTypeParameter))
    }, Map.empty, None)

  def inferValueType: ValueType = {
    polymorphicTypeSubstitutor(inferValueType = true).subst(internalType.inferValueType).asInstanceOf[ValueType]
  }

  override def removeAbstracts = ScTypePolymorphicType(internalType.removeAbstracts,
    typeParameters.map {
      case TypeParameter(parameters, lowerType, upperType, psiTypeParameter) =>
        TypeParameter(parameters, // todo: ?
          new Suspension(lowerType.v.removeAbstracts),
          new Suspension(upperType.v.removeAbstracts),
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
                new Suspension(innerUpdate(lowerType.v)),
                new Suspension(innerUpdate(upperType.v)),
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
                new Suspension(innerUpdate(lowerType.v, -variance)),
                new Suspension(innerUpdate(upperType.v, variance)),
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
        val subst = new ScSubstitutor(new collection.immutable.HashMap[(String, PsiElement), ScType] ++
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
        }), Map.empty, None)
        subst.subst(internalType).equiv(p.internalType, undefinedSubst, falseUndef)
      case _ => (false, undefinedSubst)
    }
  }

  override def visitType(visitor: TypeVisitor) = visitor match {
    case scalaVisitor: ScalaTypeVisitor => scalaVisitor.visitTypePolymorphicType(this)
    case _ =>
  }

  override def typeDepth = internalType.typeDepth.max(typeParameters.toArray.depth)
}
