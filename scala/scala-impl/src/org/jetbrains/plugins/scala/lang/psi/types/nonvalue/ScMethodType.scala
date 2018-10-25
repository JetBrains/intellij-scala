package org.jetbrains.plugins.scala
package lang.psi.types.nonvalue

import com.intellij.psi.PsiParameter
import org.jetbrains.plugins.scala.extensions.PsiParameterExt
import org.jetbrains.plugins.scala.lang.psi.ElementScope
import org.jetbrains.plugins.scala.lang.psi.api.statements.params.{ScParameter, TypeParamIdOwner}
import org.jetbrains.plugins.scala.lang.psi.types._
import org.jetbrains.plugins.scala.lang.psi.types.api._
import org.jetbrains.plugins.scala.lang.psi.types.recursiveUpdate.AfterUpdate.ProcessSubtypes
import org.jetbrains.plugins.scala.lang.psi.types.recursiveUpdate.{AfterUpdate, ScSubstitutor, Update}
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
case class Parameter(name: String,
                     deprecatedName: Option[String],
                     paramType: ScType,
                     expectedType: ScType,
                     isDefault: Boolean = false,
                     isRepeated: Boolean = false,
                     isByName: Boolean = false,
                     index: Int = -1,
                     psiParam: Option[PsiParameter] = None,
                     defaultType: Option[ScType] = None) {
  def paramInCode: Option[ScParameter] = psiParam.collect {
    case parameter: ScParameter => parameter
  }

  def nameInCode: Option[String] = psiParam.map(_.getName)

  def isImplicit: Boolean = paramInCode.exists(_.isImplicitParameter)
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
      else inferredParamType.tryWrapIntoSeqType
    }))
  }

  override def updateSubtypes(updates: Array[Update], index: Int, visited: Set[ScType]): ScMethodType = {
    def update(tp: ScType) = tp.recursiveUpdateImpl(updates, index, visited, isLazySubtype = true)
    def updateParameter(p: Parameter): Parameter = p.copy(
      paramType = update(p.paramType),
      expectedType = update(p.expectedType),
      defaultType = p.defaultType.map(update)
    )

    ScMethodType(
      returnType.recursiveUpdateImpl(updates, index, visited),
      params.map(updateParameter),
      isImplicit
    )
  }

  override def updateSubtypesVariance(update: (ScType, Variance) => AfterUpdate,
                                      variance: Variance = Covariant,
                                      revertVariances: Boolean = false)
                                     (implicit visited: Set[ScType]): ScType = {

    def updateParameterType(tp: ScType) = tp.recursiveVarianceUpdate(update, -variance, isLazySubtype = true)
    def updateParameter(p: Parameter): Parameter = p.copy(
      paramType = updateParameterType(p.paramType),
      expectedType = updateParameterType(p.expectedType),
      defaultType = p.defaultType.map(updateParameterType)
    )
    ScMethodType(
      returnType.recursiveVarianceUpdate(update, variance),
      params.map(updateParameter),
      isImplicit)
  }

  override def equivInner(r: ScType, constraints: ConstraintSystem, falseUndef: Boolean): ConstraintsResult = {
    var lastConstraints = constraints
    r match {
      case m: ScMethodType =>
        if (m.params.length != params.length) return ConstraintsResult.Left
        var t = m.returnType.equiv(returnType, lastConstraints, falseUndef)
        if (t.isLeft) return ConstraintsResult.Left
        lastConstraints = t.constraints
        var i = 0
        while (i < params.length) {
          //todo: Seq[Type] instead of Type*
          if (params(i).isRepeated != m.params(i).isRepeated) return ConstraintsResult.Left
          t = params(i).paramType.equiv(m.params(i).paramType, lastConstraints, falseUndef)
          if (t.isLeft) return ConstraintsResult.Left
          lastConstraints = t.constraints
          i = i + 1
        }
        lastConstraints
      case _ => ConstraintsResult.Left
    }
  }
}

case class ScTypePolymorphicType(internalType: ScType, typeParameters: Seq[TypeParameter]) extends NonValueType {

  if (internalType.isInstanceOf[ScTypePolymorphicType]) {
    throw new IllegalArgumentException("Polymorphic type can't have wrong internal type")
  }

  override implicit def projectContext: ProjectContext = internalType.projectContext

  def polymorphicTypeSubstitutor: ScSubstitutor =
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
          ProcessSubtypes
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
      ScAbstractType(tp, lowerType, upperType)
    }
  }

  def abstractOrLowerTypeSubstitutor: ScSubstitutor = {
    //approximation of logic from scala.tools.nsc.typechecker.Infer.Inferencer#exprTypeArgs#variance
    val forVarianceCheck = internalType match {
      case mt: ScMethodType if mt.isImplicit => mt.copy(returnType = Any)(mt.elementScope)
      case _ => internalType
    }
    ScSubstitutor.bind(typeParameters) { tp =>
      val lowerType: ScType = if (hasRecursiveTypeParameters(tp.lowerType)) Nothing else tp.lowerType
      val upperType: ScType = if (hasRecursiveTypeParameters(tp.upperType)) Any else tp.upperType

      if (lowerType.equiv(Nothing)) ScAbstractType(tp, lowerType, upperType)
      else {
        val isContraVar = tp.varianceInType(forVarianceCheck).isContravariant
        if (isContraVar) upperType else lowerType
      }
    }
  }

  def typeParameterOrLowerSubstitutor: ScSubstitutor =
    ScSubstitutor.bind(typeParameters) { tp =>
      val lowerType: ScType = if (hasRecursiveTypeParameters(tp.lowerType)) Nothing else tp.lowerType

      if (lowerType.equiv(Nothing)) TypeParameterType(tp)
      else lowerType
    }

  private lazy val typeParamIds = typeParameters.map(_.typeParamId).toSet

  private def hasRecursiveTypeParameters(typez: ScType): Boolean = typez.hasRecursiveTypeParameters(typeParamIds)

  def inferValueType: ValueType = {
    polymorphicTypeSubstitutor.subst(internalType.inferValueType).asInstanceOf[ValueType]
  }

  override def updateSubtypes(updates: Array[Update], index: Int, visited: Set[ScType]): ScType = {
    ScTypePolymorphicType(
      internalType.recursiveUpdateImpl(updates, index, visited),
      typeParameters.update(_.recursiveUpdateImpl(updates, index, visited, isLazySubtype = true))
    )
  }

  override def updateSubtypesVariance(update: (ScType, Variance) => AfterUpdate,
                                      variance: Variance = Covariant,
                                      revertVariances: Boolean = false)
                                     (implicit visited: Set[ScType]): ScType = {
    ScTypePolymorphicType(
      internalType.recursiveVarianceUpdate(update, variance),
      typeParameters.updateWithVariance(_.recursiveVarianceUpdate(update, _, isLazySubtype = true), -variance)
    )
  }

  override def equivInner(r: ScType, constraints: ConstraintSystem, falseUndef: Boolean): ConstraintsResult = {
    var lastConstraints = constraints
    r match {
      case p: ScTypePolymorphicType =>
        if (typeParameters.length != p.typeParameters.length) return ConstraintsResult.Left
        var i = 0
        while (i < typeParameters.length) {
          var t = typeParameters(i).lowerType.equiv(p.typeParameters(i).lowerType, lastConstraints, falseUndef)
          if (t.isLeft) return ConstraintsResult.Left
          lastConstraints = t.constraints
          t = typeParameters(i).upperType.equiv(p.typeParameters(i).upperType, lastConstraints, falseUndef)
          if (t.isLeft) return ConstraintsResult.Left
          lastConstraints = t.constraints
          i = i + 1
        }
        val subst = ScSubstitutor.bind(typeParameters, p.typeParameters)(TypeParameterType(_))
        subst.subst(internalType).equiv(p.internalType, lastConstraints, falseUndef)
      case _ => ConstraintsResult.Left
    }
  }

  override def visitType(visitor: TypeVisitor): Unit = visitor match {
    case scalaVisitor: ScalaTypeVisitor => scalaVisitor.visitTypePolymorphicType(this)
    case _ =>
  }

  override def typeDepth: Int = internalType.typeDepth.max(typeParameters.toArray.depth)
}
