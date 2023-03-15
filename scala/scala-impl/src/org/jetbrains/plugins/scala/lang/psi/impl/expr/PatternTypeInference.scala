package org.jetbrains.plugins.scala.lang.psi.impl.expr

import com.intellij.psi.PsiClass
import org.jetbrains.plugins.scala.extensions.{ObjectExt, PsiElementExt}
import org.jetbrains.plugins.scala.lang.psi.api.base.patterns.{ScCaseClause, ScConstructorPattern, ScPattern, ScTypedPatternLike}
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScMatch
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScFunctionDefinition
import org.jetbrains.plugins.scala.lang.psi.api.statements.params.TypeParamIdOwner
import org.jetbrains.plugins.scala.lang.psi.types.api.{ParameterizedType, PsiTypeParametersExt, TypeParameter, TypeParameterType}
import org.jetbrains.plugins.scala.lang.psi.types.recursiveUpdate.ScSubstitutor
import org.jetbrains.plugins.scala.lang.psi.types.result.TypeResult
import org.jetbrains.plugins.scala.lang.psi.types.{ConstraintSystem, ConstraintsResult, ScType, SmartSuperTypeUtil}
import org.jetbrains.plugins.scala.project.ProjectContext

import scala.annotation.tailrec


object PatternTypeInference {
  private[this] def patternType(pattern: ScPattern): TypeResult = pattern match {
    case ScTypedPatternLike(typePattern) => typePattern.typeElement.`type`()
    case other                           => other.`type`()
  }

  def doTypeInference(
    pattern:       ScPattern,
    scrutineeType: ScType,
    patternTpe:    Option[ScType]             = None,
    tps:           Option[Seq[TypeParameter]] = None,
  ): ScSubstitutor = {
    val tpe = patternTpe.getOrElse(patternType(pattern).getOrNothing)

    // For constructor patterns type inference searches for max solution
    val shouldSolveForMaxType = pattern.is[ScConstructorPattern]
    val typeVariablesNames    = pattern.typeVariables.map(_.name).toSet

    implicit val ctx: ProjectContext = pattern.projectContext

    val (classTypeParams, boundSubst) =
      tps
        .map(_ -> ScSubstitutor.empty)
        .orElse {
          for {
            (ctpe, subst) <- tpe.extractClassType
            tparams       = ctpe.getTypeParameters.instantiate
          } yield (tparams, subst)
        }
        .getOrElse(Seq.empty -> ScSubstitutor.empty)

    val typeVarsBuilder   = Seq.newBuilder[TypeParameter]
    val typeParamsBuilder = Seq.newBuilder[TypeParameter]

    //Collect type var constraints from corresponding type parameter bounds
    //in class definition
    val constraints = tpe match {
      case ParameterizedType(_, targs) if targs.size == classTypeParams.size =>
        targs.zip(classTypeParams).foldLeft(ConstraintSystem.empty) {
          case (acc, (tpt: TypeParameterType, tp: TypeParameter)) =>
            val tParam = tpt.typeParameter

            if (typeVariablesNames.contains(tParam.name)) typeVarsBuilder   += tParam
            else                                          typeParamsBuilder += tParam

            addTypeParamBounds(acc, tParam, tp, boundSubst)
          case (acc, _) => acc
        }
      case _ => ConstraintSystem.empty
    }

    val typeVars   = typeVarsBuilder.result()
    val typeParams = typeParamsBuilder.result()

    val subst =
      if (tpe.conforms(scrutineeType)) solve(constraints, shouldSolveForMaxType, typeParams, typeVars)
      else {
        val undefSubst  = ScSubstitutor.undefineTypeParams(typeVars ++ typeParams)
        val conformance = undefSubst(tpe).conforms(scrutineeType, constraints)
        val maybeSubst  = solve(conformance, shouldSolveForMaxType, typeParams, typeVars)

        maybeSubst.orElse {
          //If the above failed also instantiate type parameters of the enclosing method
          //as type variables in scrutinee type
          val enclosingFun          = pattern.parentOfType[ScFunctionDefinition]
          val enclosingTypeParams   = enclosingFun.fold(Seq.empty[TypeParameter])(_.typeParameters.map(TypeParameter(_)))
          val constraintsWithBounds = enclosingTypeParams.foldLeft(constraints)((acc, tp) => addTypeParamBounds(acc, tp, tp))
          val undefScrutinee        = ScSubstitutor.undefineTypeParams(enclosingTypeParams)

          val conformance =
            isIntersectionPopulated(
              undefSubst(tpe),
              undefScrutinee(scrutineeType),
              constraintsWithBounds
            )

          solve(conformance, shouldSolveForMaxType, typeParams ++ enclosingTypeParams, typeVars)
        }
      }

    boundSubst.followed(subst.getOrElse(ScSubstitutor.empty))
  }

  private def addTypeParamBounds(
    constraints: ConstraintSystem,
    tvar:        TypeParameter,
    param:       TypeParameter,
    boundSubst:  ScSubstitutor = ScSubstitutor.empty
  ): ConstraintSystem = {
    val id = tvar.typeParamId
    constraints.withLower(id, boundSubst(param.lowerType)).withUpper(id, boundSubst(param.upperType))
  }

  private def isIntersectionPopulated(
    patType:       ScType,
    scrutineeType: ScType,
    constraints:   ConstraintSystem
  ): ConstraintsResult = {
    import SmartSuperTypeUtil.TraverseSupers
    val scrutineeBaseBuilder = Map.newBuilder[PsiClass, ScSubstitutor]
    val patternBaseBuilder   = Map.newBuilder[PsiClass, ScSubstitutor]

    SmartSuperTypeUtil.traverseSuperTypes(scrutineeType, (_, cls, subst) => {
      scrutineeBaseBuilder += ((cls, subst)); TraverseSupers.ProcessParents
    })

    SmartSuperTypeUtil.traverseSuperTypes(patType, (_, cls, subst) => {
      patternBaseBuilder += ((cls, subst)); TraverseSupers.ProcessParents
    })

    scrutineeBaseBuilder ++= scrutineeType.extractClassType
    patternBaseBuilder ++= patType.extractClassType

    val scrutineeBaseMap = scrutineeBaseBuilder.result()
    val patternBaseMap   = patternBaseBuilder.result()

    def checkConsistent(
      cls:            PsiClass,
      patSubst:       ScSubstitutor,
      scrutineeSubst: ScSubstitutor,
      constraints:    ConstraintSystem
    ): ConstraintsResult = {
      val typeParams = cls.getTypeParameters.instantiate
      val typeParamsIter = typeParams.iterator

      checkTypeParamsConsistency(typeParamsIter, constraints, { tParam =>
        val tpt = TypeParameterType(tParam)
        val lhs = patSubst(tpt)
        val rhs = scrutineeSubst(tpt)

        if (tParam.isInvariant)          lhs.equiv(rhs, constraints, falseUndef = false)
        else if (tParam.isContravariant) lhs.conforms(rhs, constraints)
        else                             rhs.conforms(lhs, constraints)
      })
    }

    @tailrec
    def checkTypeParamsConsistency(
      tParams: Iterator[TypeParameter],
      constraints: ConstraintSystem,
      check: TypeParameter => ConstraintsResult
    ): ConstraintsResult =
      if (!tParams.hasNext) constraints
      else {
        val tParam = tParams.next()
        val checked = check(tParam)

        checked match {
          case ConstraintsResult.Left => ConstraintsResult.Left
          case cs: ConstraintSystem   => checkTypeParamsConsistency(tParams, cs, check)
        }
      }

    @tailrec
    def checkBaseClassesConsistency(
      baseClasses: Iterator[(PsiClass, ScSubstitutor)],
      constraints: ConstraintSystem
    ): ConstraintsResult =
      if (!baseClasses.hasNext) constraints
      else {
        val (cls, patternSubst) = baseClasses.next()
        val scrutineeSubst      = scrutineeBaseMap.get(cls)

        scrutineeSubst match {
          case Some(scrutineeSubst) =>
            checkConsistent(cls, patternSubst, scrutineeSubst, constraints) match {
              case ConstraintsResult.Left => ConstraintsResult.Left
              case cs: ConstraintSystem   => checkBaseClassesConsistency(baseClasses, cs)
            }
          case None => checkBaseClassesConsistency(baseClasses, constraints)
        }
      }

    checkBaseClassesConsistency(patternBaseMap.iterator, constraints)
  }

  private def solve(
    constraints:           ConstraintsResult,
    shouldSolveForMaxType: Boolean,
    typeParams:            Seq[TypeParameter],
    tvars:                 Seq[TypeParameter]
  )(implicit
    ctx: ProjectContext
  ): Option[ScSubstitutor] = {
    import ctx.stdTypes

    constraints match {
      case ConstraintsResult.Left => None
      case cs: ConstraintSystem   =>
        if (!shouldSolveForMaxType) {
          cs.substitutionBounds(canThrowSCE = false)
            .map { bounds =>
              val tParamSubst =
                ScSubstitutor.bind(typeParams)(tParam => {
                  val id = tParam.typeParamId
                  bounds.tvMap.getOrElse(id, TypeParameterType(tParam))
                })

              val tVarSubst =
                ScSubstitutor.bind(tvars)(tvar => {
                  val id = tvar.typeParamId
                  TypeParameterType(
                    TypeParameter.light(
                      tvar.name,
                      Seq.empty,
                      bounds.lowerMap.getOrElse(id, stdTypes.Nothing),
                      bounds.upperMap.getOrElse(id, stdTypes.Any)
                    )
                  )
                })

              tParamSubst.followed(tVarSubst)
            }
        }
        else
          cs.substitutionBounds(canThrowSCE = false)
            .map(bounds =>
              ScSubstitutor.bind(typeParams) { tParam =>
                val id = tParam.typeParamId
                bounds.upperMap.getOrElse(id, stdTypes.Any)
              }
            )
    }
  }

  def doForMatchClause(m: ScMatch, cc: ScCaseClause): ScSubstitutor =
    (
      for {
        scrutinee    <- m.expression
        scrutineeTpe <- scrutinee.`type`().toOption
        pattern      <- cc.pattern
      } yield PatternTypeInference.doTypeInference(pattern, scrutineeTpe)
    ).getOrElse(ScSubstitutor.empty)
}
