package org.jetbrains.plugins.scala.lang.psi.types

import com.intellij.psi.PsiClass
import org.jetbrains.plugins.scala.lang.psi.api.statements.params._
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
    * where at least one of (TC1, TC2) is a higher-kinede type variable
    * (i.e. parameterized type with [[org.jetbrains.plugins.scala.lang.psi.types.api.UndefinedType]] as its designator)
    */
  private[this] def unifyTypeVariable(
    typeVariable: ParameterizedType,
    tpe:          ParameterizedType,
    constraints:  ConstraintSystem,
    boundKind:    Bound,
    visited:      Set[PsiClass],
    checkWeak:    Boolean
  ): ConstraintsResult = {
    val (tvDes, tvArgs, addBounds) = typeVariable match {
      case ParameterizedType(UndefinedOrWildcard(tp, bounds), typeArgs) => (tp, typeArgs, bounds)
      case _ =>
        throw new IllegalArgumentException(s"Only higher-order type variables can be unified, actual: ${typeVariable.canonicalText}")
    }

    def addBound(constraints: ConstraintSystem, bound: ScType): ConstraintSystem =
      if (!addBounds) constraints
      else
        boundKind match {
          case Bound.Lower       => constraints.withLower(tvDes.typeParamId, bound)
          case Bound.Upper       => constraints.withUpper(tvDes.typeParamId, bound)
          case Bound.Equivalence => addParam(tvDes, bound, constraints)
        }

    val args = tpe.typeArguments
    val des  = tpe.designator

    val captureLength        = args.length - tvArgs.length
    val abstractedTypeParams = TypeVariableUnification.extractTypeParameters(des).drop(captureLength)
    val tvTypeParameters     = tvDes.typeParameters

    if (!unifiableKinds(abstractedTypeParams, tvTypeParameters))
      ConstraintsResult.Left
    else if (captureLength == 0) {
       /** Higher-kinded type var with same arity as `tpe` */
      checkParameterizedType(
        tvTypeParameters,
        tvArgs, args,
        addBound(constraints, des),
        visited, checkWeak, boundKind == Bound.Equivalence
      )
    } else if (captureLength > 0 && projectContext.project.isPartialUnificationEnabled) {
      /** Partial unification */
      val (captured, abstracted) = args.splitAt(captureLength)
      val conformance =
        checkParameterizedType(
          tvTypeParameters,
          tvArgs, abstracted, constraints,
          visited, checkWeak, boundKind == Bound.Equivalence
        )
      if (conformance.isRight) {
        val abstractedTypeParams = abstracted.indices.map(
          idx => TypeParameter.light("p" + idx + "$$", tvTypeParameters(idx).typeParameters, Nothing, Any)
        )

        val typeConstructor =
          ScTypePolymorphicType(
            ScParameterizedType(
              des,
              captured ++ abstractedTypeParams.map(TypeParameterType(_))
            ),
            abstractedTypeParams
          )

        addBound(conformance.constraints, typeConstructor)
      } else conformance
    } else ConstraintsResult.Left
  }

  /**
    * Recursively walk through supertypes of `tpe` until one,
    * which `hkTpe` can be unified with is found. In case there is no
    * such supertype, or when trying to check if `hkTv` is a subtype of
    * `tpe` (i.e. `boundKind` == `Bound.Upper`) returns `ConstraintsResult.Left`.
    */
  private[this] def tryUnifyParent(
    hkTv:        ParameterizedType,
    tpe:         ParameterizedType,
    constraints: ConstraintSystem,
    boundKind:   Bound,
    visited:     Set[PsiClass],
    checkWeak:   Boolean
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
                  case ConstraintsResult.Left => ProcessParents
                  case unified                => unificationConstraints = unified; Stop
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
  ): ConstraintsResult =
    unifyTypeVariable(hkTv, tpe, constraints, boundKind, visited, checkWeak) match {
      case ConstraintsResult.Left => tpe match {
        case AliasType(_, Right(lower: ParameterizedType), _) =>
          unifyHK(hkTv, lower, constraints, boundKind, visited, checkWeak)
        case _ => tryUnifyParent(hkTv, tpe, constraints, boundKind, visited, checkWeak)
      }
      case unified => unified
    }
}

object TypeVariableUnification {
  @scala.annotation.tailrec
  private final def extractTypeParameters(tpe: ScType): Seq[TypeParameter] = tpe match {
    case ParameterizedType(des, _)         => extractTypeParameters(des)
    case AliasType(alias, _, _)            => alias.typeParameters.map(TypeParameter(_))
    case ScAbstractType(tp, _, _)          => tp.typeParameters
    case ScTypePolymorphicType(_, tparams) => tparams
    case UndefinedOrWildcard(tparam, _)    => tparam.typeParameters
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
}
