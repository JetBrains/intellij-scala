package org.jetbrains.plugins.scala.lang.psi.impl.expr

import org.jetbrains.plugins.scala.extensions.{ObjectExt, PsiElementExt}
import org.jetbrains.plugins.scala.lang.psi.api.base.patterns.{ScCaseClause, ScPattern, ScTypedPattern}
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScMatch
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScFunctionDefinition
import org.jetbrains.plugins.scala.lang.psi.api.statements.params.TypeParamIdOwner
import org.jetbrains.plugins.scala.lang.psi.types.api.{ParameterizedType, PsiTypeParamatersExt, TypeParameter, TypeParameterType}
import org.jetbrains.plugins.scala.lang.psi.types.recursiveUpdate.ScSubstitutor
import org.jetbrains.plugins.scala.lang.psi.types.result.TypeResult
import org.jetbrains.plugins.scala.lang.psi.types.{ConstraintSystem, ScType}
import org.jetbrains.plugins.scala.project.ProjectContext

object PatternTypeInferenceUtil {
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

    if (tpe.conforms(scrutineeType)) ScSubstitutor.empty.toOption
    else {
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

      val typeVars    = typeVarsBuilder.result()
      val undefSubst  = ScSubstitutor.undefineTypeParams(typeVars)
      val conformance = undefSubst(tpe).conforms(scrutineeType, constraints)
      val res = conformance match {
        case ConstraintSystem(subst) => subst.toOption
        case _ =>
          for {
            enclosingFun   <- pattern.parentOfType[ScFunctionDefinition]
            typeParams     = enclosingFun.typeParameters.map(TypeParameter(_))
            undefScrutinee = ScSubstitutor.undefineTypeParams(typeParams)
            conformance    = undefSubst(tpe).conforms(undefScrutinee(scrutineeType), constraints)
            subst          <- conformance.toSubst
          } yield subst
      }

      // This is probably an incomplete solution in the presence of variance in typeParams,
      // see also ScConstructorPattern#`type`, the correct way would be to flip variance
      // and solve for max, not min type.
      res.map(_.followed(ScSubstitutor.bind(typeVars)(_.upperType)))
    }
  }

  def doForMatchClause(m: ScMatch, cc: ScCaseClause): Option[ScSubstitutor] =
    for {
      scrutinee    <- m.expression
      scrutineeTpe <- scrutinee.`type`().toOption
      pattern      <- cc.pattern
      subst        <- PatternTypeInferenceUtil.doTypeInference(pattern, scrutineeTpe)
    } yield subst
}
