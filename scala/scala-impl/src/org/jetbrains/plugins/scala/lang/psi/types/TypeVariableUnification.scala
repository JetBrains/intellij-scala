package org.jetbrains.plugins.scala.lang.psi.types

import com.intellij.psi.PsiClass
import org.jetbrains.plugins.scala.lang.psi.types.ScalaConformance._
import org.jetbrains.plugins.scala.lang.psi.types.SmartSuperTypeUtil._
import org.jetbrains.plugins.scala.lang.psi.types.api.{ParameterizedType, TypeParameter, TypeParameterType, _}
import org.jetbrains.plugins.scala.lang.psi.types.nonvalue.ScTypePolymorphicType
import org.jetbrains.plugins.scala.project._

/**
  * Conformance parts related to HK type-variable unification
  * and related kind-checking infrastructure.
  */
trait TypeVariableUnification { self: ScalaConformance with ProjectContextOwner =>
  import TypeVariableUnification._



  /**
    * Performs subtyping check of the form:
    * {{{
    * TC1[T1,..., TN] <: TC2[T'1,...,T'K]
    * }}},
    * where at least one of (TC1, TC2) is a higher-kinded type variable
    * (i.e. parameterized type with [[org.jetbrains.plugins.scala.lang.psi.types.api.UndefinedType]] as its designator)
    */
  private def unifyTypeVariable(
    typeVariable: ParameterizedType,
    tpe:          ParameterizedType,
    constraints:  ConstraintSystem,
    boundKind:    Bound,
    visited:      Set[PsiClass],
    checkWeak:    Boolean
  )(implicit
    context: Context
  ): UnificationResult = {
    val (tvDes, tvArgs) = typeVariable match {
      case ParameterizedType(UndefinedType(tp, _), typeArgs) => (tp, typeArgs)
      case _ =>
        throw new IllegalArgumentException(s"Only higher-order type variables can be unified, actual: ${typeVariable.canonicalText}")
    }

    def addBound(constraints: ConstraintSystem, bound: ScType): ConstraintSystem =
      boundKind match {
        case Bound.Lower       => constraints.withLower(tvDes, bound)
        case Bound.Upper       => constraints.withUpper(tvDes, bound)
        case Bound.Equivalence => addParam(tvDes, bound, constraints)
      }

    val args = tpe.typeArguments
    val des  = tpe.designator

    val captureLength        = args.length - tvArgs.length
    val tpeTypeParameters    = TypeVariableUnification.extractTypeParameters(des)
    val abstractedTypeParams = tpeTypeParameters.drop(captureLength)
    val tvTypeParameters     = tvDes.typeParameters

    val (lhsArgs, rhsArgs, lhsTypeParams, rhsTypeParams) = boundKind match {
      case Bound.Upper => (args, tvArgs, tpeTypeParameters, tvTypeParameters)
      case _           => (tvArgs, args, tvTypeParameters, tpeTypeParameters)
    }

    if (!unifiableKinds(abstractedTypeParams, tvTypeParameters))
      UnificationResult.Failure
    else if (captureLength == 0) {
       /** Higher-kinded type var with the same arity as `tpe` */
      val unifiedConstraints = checkParameterizedType(
        lhsTypeParams,
        lhsArgs,
        rhsArgs,
        addBound(constraints, des),
        visited,
        checkWeak,
        boundKind == Bound.Equivalence
      )

      UnificationResult.Success(unifiedConstraints)
    } else if (captureLength > 0 && projectContext.project.isPartialUnificationEnabled) {
      /** Partial unification */
      val (captured, abstracted) = rhsArgs.splitAt(captureLength)

      val boundsFit = checkTypeConstructorParameterBounds(lhsTypeParams, rhsTypeParams.drop(captureLength))

      if (!boundsFit) {
        if (context.isScala3) return UnificationResult.Failure //this is a normal failure under scala 3, retry with super types
        else                  return UnificationResult.TypeParameterBoundViolated
      }

      val conformance =
        checkParameterizedType(
          tvTypeParameters,
          lhsArgs,
          abstracted,
          constraints,
          visited,
          checkWeak,
          boundKind == Bound.Equivalence
        )

      if (conformance.isRight) {
        val abstractedTypeParams = abstracted.indices.map { idx =>
          val tvTp = tvTypeParameters(idx)
          TypeParameter.light("p" + idx + "$$", tvTp.typeParameters, tvTp.lowerType, tvTp.upperType)
        }

        val typeConstructor =
          ScTypePolymorphicType(
            ScParameterizedType(
              des,
              captured ++ abstractedTypeParams.map(TypeParameterType(_))
            ),
            abstractedTypeParams
          )

        val constraintsWithBound = addBound(conformance.constraints, typeConstructor)

        UnificationResult.Success(constraintsWithBound)
      } else UnificationResult.Success(conformance)
    } else UnificationResult.Failure
  }

  private[this] def checkTypeConstructorParameterBounds(
    lhsTypeParams:        Seq[TypeParameter],
    abstractedTypeParams: Seq[TypeParameter]
  ): Boolean = {
    lhsTypeParams.zip(abstractedTypeParams).forall { case (lhsTparam, abstractedTypeParam) =>
      lhsTparam.upperType.conforms(abstractedTypeParam.upperType) &&
        abstractedTypeParam.lowerType.conforms(lhsTparam.lowerType)
    }
  }

  /**
    * Recursively walk through supertypes of `tpe` until one,
    * which `hkTpe` can be unified with is found. In case there is no
    * such supertype, or when trying to check if `hkTv` is a subtype of
    * `tpe` (i.e. `boundKind` == `Bound.Upper`) returns `ConstraintsResult.Left`.
    */
  private def tryUnifyParent(
    hkTv:        ParameterizedType,
    tpe:         ParameterizedType,
    constraints: ConstraintSystem,
    boundKind:   Bound,
    visited:     Set[PsiClass],
    checkWeak:   Boolean
  )(implicit
    context: Context
  ): ConstraintsResult = {
    import SmartSuperTypeUtil.TraverseSupers._

    var unificationConstraints: ConstraintsResult = ConstraintsResult.Left

    boundKind match {
      case Bound.Lower =>
        traverseSuperTypes(tpe, (tp, _, _) =>
            tp match {
              case ptpe: ParameterizedType =>
                val tryUnify = unifyTypeVariable(hkTv, ptpe, constraints, boundKind, visited, checkWeak)
                tryUnify match {
                  case UnificationResult.TypeParameterBoundViolated =>
                    unificationConstraints = ConstraintsResult.Left; Stop
                  case UnificationResult.Failure => ProcessParents
                  case UnificationResult.Success(unified) => unificationConstraints = unified; Stop
                }
              case _ => ProcessParents
            }
        )
        unificationConstraints
      case Bound.Upper | Bound.Equivalence => unificationConstraints
    }
  }

  /**
    * Tries to unify given higher-kinded type variable by
    * first unwrapping type aliases and then consecutively
    * going through `tpe` supertypes until unifiable type is found.
    */
  @scala.annotation.tailrec
  private[psi] final def unifyHK(
    hkTv:        ParameterizedType,
    tpe:         ParameterizedType,
    constraints: ConstraintSystem,
    boundKind:   Bound,
    visited:     Set[PsiClass],
    checkWeak:   Boolean
  )(implicit
    context: Context
  ): ConstraintsResult =
    unifyTypeVariable(hkTv, tpe, constraints, boundKind, visited, checkWeak) match {
      case UnificationResult.TypeParameterBoundViolated => ConstraintsResult.Left
      case UnificationResult.Failure =>
        tpe match {
          case AliasType(_, Right(lower: ParameterizedType), _, _) =>
            unifyHK(hkTv, lower, constraints, boundKind, visited, checkWeak)
          case _ => tryUnifyParent(hkTv, tpe, constraints, boundKind, visited, checkWeak)
        }
      case UnificationResult.Success(constraints) => constraints
    }
}

object TypeVariableUnification {
  @scala.annotation.tailrec
  final def extractTypeParameters(tpe: ScType): Seq[TypeParameter] = tpe match {
    case ParameterizedType(des, _)         => extractTypeParameters(des)
    case AliasType(alias, _, _, _)         => alias.typeParameters.map(TypeParameter(_))
    case ScAbstractType(tp, _, _)          => tp.typeParameters
    case ScTypePolymorphicType(_, tparams) => tparams
    case UndefinedType(tparam, _)          => tparam.typeParameters
    case tpt: TypeParameterType            => tpt.typeParameters
    case other =>
      other.extractClass.fold(Seq.empty[TypeParameter])(_.getTypeParameters.instantiate)
  }

  private[psi] def unifiableKinds(lhs: ScType, rhs: ScType): Boolean =  {
    val lhsParams = extractTypeParameters(lhs)
    val rhsParams = extractTypeParameters(rhs)
    unifiableKinds(lhsParams, rhsParams)
  }

  def unifiableKinds(lhsParams: Iterable[TypeParameter], rhsParams: Iterable[TypeParameter]): Boolean = {
    lhsParams.size == rhsParams.size && lhsParams.zip(rhsParams).forall {
      case (l, r) => unifiableKinds(l.typeParameters, r.typeParameters)
    }
  }

  private sealed trait UnificationResult

  private object UnificationResult {
    case object TypeParameterBoundViolated              extends UnificationResult
    case object Failure                                 extends UnificationResult
    case class  Success(constraints: ConstraintsResult) extends UnificationResult
  }

}
