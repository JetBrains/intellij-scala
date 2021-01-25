package org.jetbrains.plugins.scala.lang.psi.impl.expr

import org.jetbrains.plugins.scala.extensions.{ObjectExt, PsiElementExt}
import org.jetbrains.plugins.scala.lang.psi.api.base.patterns.{ScCaseClause, ScConstructorPattern, ScPattern, ScTypedPattern}
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScMatch
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScFunctionDefinition
import org.jetbrains.plugins.scala.lang.psi.api.statements.params.TypeParamIdOwner
import org.jetbrains.plugins.scala.lang.psi.types.api.{ParameterizedType, PsiTypeParametersExt, TypeParameter, TypeParameterType}
import org.jetbrains.plugins.scala.lang.psi.types.recursiveUpdate.ScSubstitutor
import org.jetbrains.plugins.scala.lang.psi.types.result.TypeResult
import org.jetbrains.plugins.scala.lang.psi.types.{ConstraintSystem, ConstraintsResult, ScType}
import org.jetbrains.plugins.scala.project.ProjectContext

object PatternTypeInference {
  private[this] def patternType(pattern: ScPattern): TypeResult = pattern match {
    case ScTypedPattern(te) => te.`type`()
    case other              => other.`type`()
  }

  def doTypeInference(
    pattern:       ScPattern,
    scrutineeType: ScType,
    patternTpe:    Option[ScType]             = None,
    tps:           Option[Seq[TypeParameter]] = None,
  ): Option[ScSubstitutor] = {
    val tpe = patternTpe.getOrElse(patternType(pattern).getOrNothing)

    // For constructor patterns type inference searches for max solution, not min
    val shouldSolveForMaxType = pattern.is[ScConstructorPattern]

    implicit val ctx: ProjectContext = pattern.projectContext

    val (typeParams, boundSubst) =
      tps
        .map(_ -> ScSubstitutor.empty)
        .orElse {
          for {
            (ctpe, subst) <- tpe.extractClassType
            tparams       = ctpe.getTypeParameters.instantiate
          } yield (tparams, subst)
        }
        .getOrElse(Seq.empty -> ScSubstitutor.empty)

    val typeVarsBuilder = Seq.newBuilder[TypeParameter]

    //Collect type var constraints from corresponding type parameter bounds
    //in class definition
    val constraints = tpe match {
      case ParameterizedType(_, targs) if targs.size == typeParams.size =>
        targs.zip(typeParams).foldLeft(ConstraintSystem.empty) {
          case (acc, (tpt: TypeParameterType, tp: TypeParameter)) =>
            typeVarsBuilder += tpt.typeParameter
            addTypeParamBounds(acc, tp, boundSubst)
          case (acc, _) => acc
        }
      case _ => ConstraintSystem.empty
    }

    val typeVars = typeVarsBuilder.result()

    val subst =
      if (tpe.conforms(scrutineeType)) solve(constraints, shouldSolveForMaxType)
      else {
        val undefSubst  = ScSubstitutor.undefineTypeParams(typeVars)
        val conformance = undefSubst(tpe).conforms(scrutineeType, constraints)
        val maybeSubst  = solve(conformance, shouldSolveForMaxType)

        maybeSubst.orElse(
          //If the above failed also instntiate type parameters of the enclosing method
          //as type variables in scrutinee type
          for {
            enclosingFun          <- pattern.parentOfType[ScFunctionDefinition]
            enclosingTypeParams   = enclosingFun.typeParameters.map(TypeParameter(_))
            constraintsWithBounds = enclosingTypeParams.foldLeft(constraints)(addTypeParamBounds(_, _))
            undefScrutinee        = ScSubstitutor.undefineTypeParams(enclosingTypeParams)
            conformance           = isIntersectionPopulated(undefSubst(tpe), undefScrutinee(scrutineeType), constraintsWithBounds)
            subst                 <- solve(conformance, shouldSolveForMaxType)
          } yield subst
        )
      }

    subst.map(_.followed(ScSubstitutor.bind(typeVars)(_.upperType)))
  }

  private def addTypeParamBounds(
    constraints: ConstraintSystem,
    param:       TypeParameter,
    boundSubst:  ScSubstitutor = ScSubstitutor.empty
  ): ConstraintSystem = {
    val id = param.typeParamId
    if (param.isContravariant) constraints.withUpper(id, boundSubst(param.upperType))
    else                       constraints.withLower(id, boundSubst(param.lowerType))
  }

  private def isIntersectionPopulated(
    patType:       ScType,
    scrutineeType: ScType,
    constraints:   ConstraintSystem
  ): ConstraintsResult = {
    //@TODO: this is only the case if matching against final class type in patType
    //       in more intricate cases we would have to traverse and compare base class types
    patType.conforms(scrutineeType, constraints)
  }

  private def solve(
    constraints: ConstraintsResult,
    shouldSolveForMaxType: Boolean
  )(implicit
    ctx: ProjectContext
  ): Option[ScSubstitutor] =
    constraints match {
      case ConstraintsResult.Left => None
      case cs: ConstraintSystem   =>
        if (!shouldSolveForMaxType) cs.toSubst
        else
          cs.substitutionBounds(canThrowSCE = false)
            .map(bounds => ScSubstitutor(bounds.upperMap))
    }

  def doForMatchClause(m: ScMatch, cc: ScCaseClause): Option[ScSubstitutor] =
    for {
      scrutinee    <- m.expression
      scrutineeTpe <- scrutinee.`type`().toOption
      pattern      <- cc.pattern
      subst        <- PatternTypeInference.doTypeInference(pattern, scrutineeTpe)
    } yield subst
}
