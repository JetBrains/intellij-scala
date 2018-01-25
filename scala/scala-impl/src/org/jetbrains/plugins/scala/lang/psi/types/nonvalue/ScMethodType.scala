package org.jetbrains.plugins.scala
package lang.psi.types.nonvalue

import com.intellij.psi.PsiParameter
import org.jetbrains.plugins.scala.extensions.PsiParameterExt
import org.jetbrains.plugins.scala.lang.psi.ElementScope
import org.jetbrains.plugins.scala.lang.psi.api.statements.params.{TypeParamIdOwner, ScParameter}
import org.jetbrains.plugins.scala.lang.psi.types._
import org.jetbrains.plugins.scala.lang.psi.types.api._
import org.jetbrains.plugins.scala.lang.psi.types.api.designator.ScDesignatorType
import org.jetbrains.plugins.scala.lang.psi.types.recursiveUpdate.Update
import org.jetbrains.plugins.scala.lang.psi.types.result._
import org.jetbrains.plugins.scala.project.ProjectContext

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
      val `type` = scParameter.`type`().getOrNothing

      new Parameter(name = scParameter.name,
        deprecatedName = scParameter.deprecatedName,
        paramType = `type`,
        expectedType = `type`,
        isDefault = scParameter.isDefaultParam,
        isRepeated = scParameter.isRepeatedParameter,
        isByName = scParameter.isCallByNameParameter,
        index = scParameter.index,
        psiParam = Some(scParameter),
        defaultType = scParameter.getDefaultExpression.flatMap(_.`type`().toOption))
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
                       (implicit val elementScope: ElementScope) extends NonValueType {
  implicit def projectContext: ProjectContext = elementScope.projectContext

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

  override def updateSubtypes(update: Update, visited: Set[ScType]): ScMethodType = {
    ScMethodType(
      returnType.recursiveUpdateImpl(update, visited),
      params.map(p => p.copy(paramType = p.paramType.recursiveUpdateImpl(update, visited))),
      isImplicit
    )
  }

  override def recursiveVarianceUpdateModifiable[T](data: T, update: (ScType, Variance, T) => (Boolean, ScType, T),
                                                    variance: Variance = Covariant, revertVariances: Boolean = false): ScType = {
    update(this, variance, data) match {
      case (true, res, _) => res
      case (_, _, newData) =>
        ScMethodType(returnType.recursiveVarianceUpdateModifiable(newData, update, variance),
          params.map(p => p.copy(paramType = p.paramType.recursiveVarianceUpdateModifiable(newData, update, -variance))),
          isImplicit)
    }
  }

  override def equivInner(r: ScType, uSubst: ScUndefinedSubstitutor, falseUndef: Boolean): (Boolean, ScUndefinedSubstitutor) = {
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

  override implicit def projectContext: ProjectContext = internalType.projectContext

  def polymorphicTypeSubstitutor: ScSubstitutor = polymorphicTypeSubstitutor(inferValueType = false)

  def polymorphicTypeSubstitutor(inferValueType: Boolean): ScSubstitutor =
    ScSubstitutor.bind(typeParameters) { tp =>
      var contraVariant = 0
      var coOrInVariant = 0
      internalType.recursiveVarianceUpdate {
        case (typez: ScType, v: Variance) =>
          val typeParamId = typez match {
            case t: TypeParameterType => t.typeParamId
            case UndefinedType(t, _) => t.typeParamId
            case ScAbstractType(t, _, _) => t.typeParamId
            case _ => -1L
          }
          if (typeParamId > 0) {
            if (tp.typeParamId == typeParamId) {
              if (v == Contravariant) contraVariant += 1
              else coOrInVariant += 1
            }
          }
          (false, typez)
      }
      if (coOrInVariant == 0 && contraVariant != 0)
        tp.upperType.inferValueType
      else
        tp.lowerType.inferValueType
    }

  def abstractTypeSubstitutor: ScSubstitutor = {
    ScSubstitutor.bind(typeParameters){tp =>
      val lowerType: ScType = if (hasRecursiveTypeParameters(tp.lowerType)) Nothing else tp.lowerType
      val upperType: ScType = if (hasRecursiveTypeParameters(tp.upperType)) Any else tp.upperType
      ScAbstractType(TypeParameterType(tp.psiTypeParameter), lowerType, upperType)
    }
  }

  def abstractOrLowerTypeSubstitutor: ScSubstitutor =
    ScSubstitutor.bind(typeParameters) { tp =>
      val lowerType: ScType = if (hasRecursiveTypeParameters(tp.lowerType)) Nothing else tp.lowerType
      val upperType: ScType = if (hasRecursiveTypeParameters(tp.upperType)) Any else tp.upperType

      if (lowerType.equiv(Nothing)) ScAbstractType(TypeParameterType(tp.psiTypeParameter), lowerType, upperType)
      else lowerType
    }

  private lazy val typeParamIds = typeParameters.map(_.typeParamId).toSet

  private def hasRecursiveTypeParameters(typez: ScType): Boolean = typez.hasRecursiveTypeParameters(typeParamIds)

  def typeParameterTypeSubstitutor: ScSubstitutor =
    ScSubstitutor.bind(typeParameters)(TypeParameterType(_))

  def inferValueType: ValueType = {
    polymorphicTypeSubstitutor(inferValueType = true).subst(internalType.inferValueType).asInstanceOf[ValueType]
  }

  override def removeAbstracts = ScTypePolymorphicType(internalType.removeAbstracts,
    typeParameters.map {
      case TypeParameter(parameters, lowerType, upperType, psiTypeParameter) =>
        TypeParameter(parameters, // todo: ?
          lowerType.removeAbstracts,
          upperType.removeAbstracts,
          psiTypeParameter)
    })

  override def updateSubtypes(update: Update, visited: Set[ScType]): ScType = {
    ScTypePolymorphicType(
      internalType.recursiveUpdateImpl(update, visited),
      typeParameters.map {
        case TypeParameter(parameters, lowerType, upperType, psiTypeParameter) =>
          TypeParameter(parameters, // TODO: ?
            lowerType.recursiveUpdateImpl(update, visited, isLazySubtype = true),
            upperType.recursiveUpdateImpl(update, visited, isLazySubtype = true),
            psiTypeParameter)
      })
  }

  override def recursiveVarianceUpdateModifiable[T](data: T, update: (ScType, Variance, T) => (Boolean, ScType, T),
                                                    v: Variance = Covariant, revertVariances: Boolean = false): ScType = {
    update(this, v, data) match {
      case (true, res, _) => res
      case (_, _, newData) =>
        def innerUpdate(`type`: ScType, variance: Variance) =
          `type`.recursiveVarianceUpdateModifiable(newData, update, variance)

        ScTypePolymorphicType(innerUpdate(internalType, v),
          typeParameters.map {
            case TypeParameter(parameters, lowerType, upperType, psiTypeParameter) =>
              TypeParameter(parameters, // TODO: ?
                innerUpdate(lowerType, -v),
                innerUpdate(upperType, v),
                psiTypeParameter)
          })
    }
  }

  override def equivInner(r: ScType, uSubst: ScUndefinedSubstitutor, falseUndef: Boolean): (Boolean, ScUndefinedSubstitutor) = {
    var undefinedSubst = uSubst
    r match {
      case p: ScTypePolymorphicType =>
        if (typeParameters.length != p.typeParameters.length) return (false, undefinedSubst)
        var i = 0
        while (i < typeParameters.length) {
          var t = typeParameters(i).lowerType.equiv(p.typeParameters(i).lowerType, undefinedSubst, falseUndef)
          if (!t._1) return (false, undefinedSubst)
          undefinedSubst = t._2
          t = typeParameters(i).upperType.equiv(p.typeParameters(i).upperType, undefinedSubst, falseUndef)
          if (!t._1) return (false, undefinedSubst)
          undefinedSubst = t._2
          i = i + 1
        }
        val subst = ScSubstitutor.bind(typeParameters, p.typeParameters)(TypeParameterType(_))
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
