package org.jetbrains.plugins.scala.lang.psi.types.nonvalue

import org.jetbrains.plugins.scala.lang.psi.api.statements.params.TypeParamIdOwner
import org.jetbrains.plugins.scala.lang.psi.types.ConstraintSystem.SubstitutionBounds
import org.jetbrains.plugins.scala.lang.psi.types._
import org.jetbrains.plugins.scala.lang.psi.types.api._
import org.jetbrains.plugins.scala.lang.psi.types.recursiveUpdate.AfterUpdate.ProcessSubtypes
import org.jetbrains.plugins.scala.lang.psi.types.recursiveUpdate.ScSubstitutor
import org.jetbrains.plugins.scala.project.ProjectContext

final case class ScTypePolymorphicType(internalType: ScType, typeParameters: Seq[TypeParameter]) extends NonValueType {

  if (internalType.isInstanceOf[ScTypePolymorphicType]) {
    throw new IllegalArgumentException("Polymorphic type can't have wrong internal type")
  }

  override implicit def projectContext: ProjectContext = internalType.projectContext

  def polymorphicTypeSubstitutor: ScSubstitutor =
    ScSubstitutor.bind(typeParameters) { tp =>
      var contraVariant = 0
      var coOrInVariant = 0
      internalType.recursiveVarianceUpdate() {
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

  /**
    * See [[scala.tools.nsc.typechecker.Infer.Inferencer#protoTypeArgs]]
    */
  def argsProtoTypeSubst(pt: ScType): ScSubstitutor = internalType match {
    case ScMethodType(retTpe, params, _) =>
      val subst             = ScSubstitutor.bind(typeParameters)(UndefinedType(_))
      val retTpeConformance = subst(retTpe).isConservativelyCompatible(pt)

      if (retTpeConformance.isLeft) abstractTypeSubstitutor
      else
        retTpeConformance.constraints.substitutionBounds(canThrowSCE = false) match {
          case Some(SubstitutionBounds(_, lowerMap, upperMap)) =>
            ScSubstitutor.bind(typeParameters) { tp =>
              val varianceInParams = params.map(_.paramType).foldLeft(Variance.Bivariant) {
                case (acc, tpe) => acc & tp.varianceInType(tpe)
              }

              val bound = (lower: Boolean) => {
                val combine: (ScType, ScType) => ScType = if (lower) _ lub _      else _ glb _
                val map                                 = if (lower) lowerMap     else upperMap
                val original                            = if (lower) tp.lowerType else tp.upperType

                map.get(tp.typeParamId) match {
                  case Some(b) => combine(b, original)
                  case None    => original
                }
              }

              val loBound      = if (hasRecursiveTypeParameters(tp.lowerType)) Nothing else bound(true)
              val hiBound      = if (hasRecursiveTypeParameters(tp.upperType)) Any     else bound(false)
              val emptyLoBound = loBound.equiv(Nothing)
              val emptyHiBound = hiBound.equiv(Any)

              if (!emptyLoBound && varianceInParams.isContravariant)
                loBound
              else if (!emptyHiBound && (varianceInParams.isPositive || !emptyLoBound && hiBound.conforms(loBound)))
                hiBound
              else ScAbstractType(tp, loBound, hiBound)
            }
          case None => abstractTypeSubstitutor
        }
    case _ => throw new IllegalArgumentException("argProtoTypeSubst call on non-poly-method type")
  }

  def abstractOrLowerTypeSubstitutor: ScSubstitutor = {
    //approximation of logic from scala.tools.nsc.typechecker.Infer.Inferencer#exprTypeArgs#variance
    val forVarianceCheck = internalType match {
      case mt: ScMethodType if mt.isImplicit => mt.copy(result = Any)(mt.elementScope)
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
    polymorphicTypeSubstitutor(internalType.inferValueType).asInstanceOf[ValueType]
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
        subst(internalType).equiv(p.internalType, lastConstraints, falseUndef)
      case _ => ConstraintsResult.Left
    }
  }

  override def visitType(visitor: TypeVisitor): Unit = visitor match {
    case scalaVisitor: ScalaTypeVisitor => scalaVisitor.visitTypePolymorphicType(this)
    case _ =>
  }

  override def typeDepth: Int = internalType.typeDepth.max(typeParameters.toArray.depth)
}