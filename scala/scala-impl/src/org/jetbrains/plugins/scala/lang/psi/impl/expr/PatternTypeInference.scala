package org.jetbrains.plugins.scala.lang.psi.impl.expr

import org.jetbrains.plugins.scala.extensions.PsiElementExt
import org.jetbrains.plugins.scala.lang.psi.api.base.patterns.{ScCaseClause, ScPattern, ScTypedPattern}
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScMatch
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScFunctionDefinition
import org.jetbrains.plugins.scala.lang.psi.api.statements.params.TypeParamIdOwner
import org.jetbrains.plugins.scala.lang.psi.types.api.{ParameterizedType, PsiTypeParamatersExt, TypeParameter, TypeParameterType}
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
    patternTpe:    Option[ScType] = None,
    tps:           Option[Seq[TypeParameter]] = None
  ): Option[ScSubstitutor] = {
    val tpe = patternTpe.getOrElse(patternType(pattern).getOrNothing)

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

    val typeVarsBuilder = Array.newBuilder[TypeParameter]

    val constraints = tpe match {
      case ParameterizedType(_, targs) if targs.size == typeParams.size =>
        targs.zip(typeParams).foldLeft(ConstraintSystem.empty) {
          case (acc, (tpt: TypeParameterType, tp: TypeParameter)) =>
            val param = tpt.typeParameter
            typeVarsBuilder += param
            val id = param.typeParamId
            if (tp.isContravariant) acc.withUpper(id, boundSubst(tp.upperType))
            else                    acc.withLower(id, boundSubst(tp.lowerType))
          case (acc, _) => acc
        }
      case _ => ConstraintSystem.empty
    }

    val typeVars = typeVarsBuilder.result()

    val subst =
      if (tpe.conforms(scrutineeType)) solveForMaxType(constraints)
      else {
        val undefSubst  = ScSubstitutor.undefineTypeParams(typeVars)
        val conformance = undefSubst(tpe).conforms(scrutineeType, constraints)
        conformance match {
          case cs: ConstraintSystem => solveForMaxType(cs)
          case _ =>
            for {
              enclosingFun   <- pattern.parentOfType[ScFunctionDefinition]
              typeParams     = enclosingFun.typeParameters.map(TypeParameter(_))
              undefScrutinee = ScSubstitutor.undefineTypeParams(typeParams)
              conformance    = undefSubst(tpe).conforms(undefScrutinee(scrutineeType), constraints)
              subst          <- solveForMaxType(conformance)
            } yield subst
        }
      }

    subst.map(_.followed(ScSubstitutor.bind(typeVars)(_.upperType)))
  }

  private def solveForMaxType(
    constraints: ConstraintsResult
  )(implicit
    ctx: ProjectContext
  ): Option[ScSubstitutor] =
    constraints match {
      case ConstraintsResult.Left => None
      case cs: ConstraintSystem =>
        cs.substitutionBounds(canThrowSCE = true)
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
