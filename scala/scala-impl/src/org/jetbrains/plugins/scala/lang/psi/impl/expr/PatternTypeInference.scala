package org.jetbrains.plugins.scala.lang.psi.impl.expr

import com.intellij.psi.PsiClass
import com.intellij.psi.util.PsiTreeUtil
import org.jetbrains.plugins.scala.caches.{BlockModificationTracker, cachedInUserData}
import org.jetbrains.plugins.scala.extensions.{ObjectExt, PsiElementExt}
import org.jetbrains.plugins.scala.lang.psi.api.base.patterns._
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScMatch
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScFunctionDefinition
import org.jetbrains.plugins.scala.lang.psi.api.statements.params.TypeParamIdOwner
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScTemplateDefinition
import org.jetbrains.plugins.scala.lang.psi.types._
import org.jetbrains.plugins.scala.lang.psi.types.api.designator.ScThisType
import org.jetbrains.plugins.scala.lang.psi.types.api._
import org.jetbrains.plugins.scala.lang.psi.types.recursiveUpdate.ScSubstitutor
import org.jetbrains.plugins.scala.lang.resolve.ScalaResolveResult
import org.jetbrains.plugins.scala.lang.resolve.processor.ExpandedExtractorResolveProcessor
import org.jetbrains.plugins.scala.project.ProjectContext

import scala.annotation.tailrec


/**
 * SLS 8.3 [[https://www.scala-lang.org/files/archive/spec/2.13/08-pattern-matching.html#type-parameter-inference-in-patterns]]
 * Basic idea of our implementation is as follows: every time resolve or a similar type-related subsystem
 * does upwards PSI-tree traversal and encounters a case clause with a pattern `p`, it accumulates knowledge about type variables
 * defined inside `p` as well as type parameters of the enclosing method in form of [[ScSubstitutor]]s (see [[ScalaResolveResult.matchClauseSubstitutor]]).
 * Most notable usages: [[org.jetbrains.plugins.scala.lang.resolve.ReferenceExpressionResolver]], [[org.jetbrains.plugins.scala.lang.psi.impl.base.ScStableCodeReferenceImpl]],
 * [[ExpectedTypesImpl]].
 *
 * It is also used for calculating types of various patterns, see [[org.jetbrains.plugins.scala.lang.psi.impl.base.types.ScTypeVariableTypeElementImpl]],
 * [[org.jetbrains.plugins.scala.lang.psi.impl.base.patterns.ScConstructorPatternImpl]], [[org.jetbrains.plugins.scala.lang.psi.impl.base.patterns.ScTypedPatternImpl]]
 *
 */
object PatternTypeInference {
  /**
   * If no type inference should be done for this pattern (e.g. it is neither [[ScExtractorPattern]], nor [[ScTypedPattern]]
   * or unapply method of this extractor pattern is directly applicable to the scrutinee type) return `Left(conformanceSubst)`,
   * otherwise calculate type of the pattern and substitutor, corresponding to the unapply method, for further processing.
   */
  @tailrec
  private[this] def getPatternType(
    pattern:       ScPattern,
    scrutineeType: ScType
  ): Either[ScSubstitutor, (ScType, ScSubstitutor)] = {
    val noTypeInference = Left(ScSubstitutor.empty)

    def emptySubst(tpe: ScType): Either[ScSubstitutor, (ScType, ScSubstitutor)] =
      Right((tpe, ScSubstitutor.empty))

    pattern match {
      case ScNamingPattern(named)           => getPatternType(named, scrutineeType)
      case ScTypedPatternLike(typePattern)  => emptySubst(typePattern.typeElement.`type`().getOrNothing)
      case ScParenthesisedPattern(inner)    => getPatternType(inner, scrutineeType)
      case stable: ScStableReferencePattern => emptySubst(stable.`type`().getOrNothing)
      case extractor: ScExtractorPattern =>
        val unapplySrr = ExpandedExtractorResolveProcessor.resolveActualUnapply(extractor.ref)

        unapplySrr match {
          case Some(ScalaResolveResult(fun: ScFunctionDefinition, subst)) =>
            val clsParent     = PsiTreeUtil.getContextOfType(pattern, true, classOf[ScTemplateDefinition]).toOption
            val withThisType  = clsParent.fold(ScSubstitutor.empty)(cls => ScSubstitutor(ScThisType(cls)))
            val combinedSubst = subst.followed(withThisType)
            val maybeTpe      = fun.parameters.head.`type`().map(combinedSubst)

            maybeTpe match {
              case Right(tpe) =>
                val undefineTypeParams       = ScSubstitutor.undefineTypeParams(fun.typeParameters.map(TypeParameter(_)))
                val simpleApplicabilitySubst = scrutineeType.conformanceSubstitutor(undefineTypeParams(tpe))

                simpleApplicabilitySubst match {
                  case Some(applicabilitySubst) => Left(combinedSubst.followed(applicabilitySubst))
                  case None                     => Right((tpe, combinedSubst))
                }
              case _ => noTypeInference
            }

          case _ => noTypeInference
        }
      case _ => noTypeInference
    }
  }

  /**
   * Do component-wise pattern type inference and return combined substitutor.
   */
  private def doForTuplePattern(tuplePattern: ScTuplePattern, scrutineeType: ScType): ScSubstitutor = {
    val patterns = tuplePattern.patternList.toSeq.flatMap(_.patterns)

    scrutineeType match {
      case TupleType(comps) if comps.size == patterns.size =>
        patterns.zip(comps).foldLeft(ScSubstitutor.empty) {
          case (acc, (pattern, scrutinee)) =>
            acc.followed(doTypeInference(pattern, scrutinee))
        }
      case _ => ScSubstitutor.empty
    }
  }

  /**
   * For given `pattern` and `scrutineeType` implement a multi-step process of pattern type inference:
   * (1) Collect initial constraints from bounds of the corresponding type parameters
   * (2) Check if pattern type conforms to scrutinee type as is, stop if it does.
   * (3) Instantiate type variables as undefined types in pattern type and repeat conformance check from step (2)
   * (4) Instantiate type parameters of an enclosing method as undefined types in scrutinee type and
   *     check if the intersection of pattern and scrutinee type is populated.
   */
  def doTypeInference(
    pattern:       ScPattern,
    scrutineeType: ScType
  ): ScSubstitutor = pattern match {
    case tuple: ScTuplePattern => doForTuplePattern(tuple, scrutineeType)
    case _ =>
      cachedInUserData(
        "doTypeInference",
        pattern,
        BlockModificationTracker(pattern),
        Tuple1(scrutineeType),
      ) {
        getPatternType(pattern, scrutineeType) match {
          case Left(subst) => subst
          case Right((tpe, unapplySubst)) =>
            // For constructor patterns type inference searches for max solution
            val shouldSolveForMaxType = pattern.is[ScExtractorPattern]
            val typeVariablesNames    = pattern.typeVariables.map(_.name).toSet

            implicit val ctx: ProjectContext = pattern.projectContext

            val (classTypeParams, boundsSubst) =
              (for {
                (ctpe, subst) <- tpe.extractClassType
                tparams       = ctpe.getTypeParameters.instantiate
              } yield (tparams, subst)).getOrElse(Seq.empty -> ScSubstitutor.empty)

            val typeVarsBuilder = Seq.newBuilder[TypeParameter]



            /**
             * (1)
             * The initial constraints set `C0` reflects just the bounds of type variables.
             * That is, assuming `tpe` has bound type variables `a1, ..., an`
             * which correspond to class type parameters `a'1, ..., a'n`
             * with lower bounds `L1, ..., Ln` and upper bounds `U1, ..., Un`
             */
            def collectConstraintsAndTypeParams(tpe: ScType): ConstraintSystem = tpe match {
              case ScAndType(lhs, rhs) =>
                collectConstraintsAndTypeParams(lhs) + collectConstraintsAndTypeParams(rhs)
              case ScOrType(lhs, rhs) =>
                collectConstraintsAndTypeParams(lhs) + collectConstraintsAndTypeParams(rhs)
              case ScCompoundType(comps, _, _) =>
                comps.foldLeft(ConstraintSystem.empty) {
                  case (acc, comp) => acc + collectConstraintsAndTypeParams(comp)
                }
              case ParameterizedType(_, targs) if targs.size == classTypeParams.size =>
                targs.zip(classTypeParams).foldLeft(ConstraintSystem.empty) {
                  case (acc, (tpt: TypeParameterType, tp: TypeParameter)) =>
                    val tParam = tpt.typeParameter

                    //@TODO: should we add constraints from unapply type parameters too
                    //       (in case of custom unapply)???
                    if (typeVariablesNames.contains(tParam.name) || shouldSolveForMaxType) {
                      typeVarsBuilder += tParam
                      addTypeParamBounds(acc, tParam, tp, boundsSubst.followed(unapplySubst))
                    } else acc
                  case (acc, _) => acc
                }
              case _ => ConstraintSystem.empty
            }

            val constraints = collectConstraintsAndTypeParams(tpe)
            val typeVars    = typeVarsBuilder.result()

            val subst =
              /*(2)*/
              if (tpe.conforms(scrutineeType)) solve(constraints, shouldSolveForMaxType, typeVars, Seq.empty)
              else {
                /*(3)*/
                val undefSubst  = ScSubstitutor.undefineTypeParams(typeVars)
                val conformance = undefSubst(tpe).conforms(scrutineeType, constraints)
                val maybeSubst  = solve(conformance, shouldSolveForMaxType, typeVars, Seq.empty)

                maybeSubst.orElse {
                  /*(4)*/
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

                  solve(conformance, shouldSolveForMaxType, typeVars, enclosingTypeParams)
                }
              }

            unapplySubst.followed(subst.getOrElse(ScSubstitutor.empty))
        }
      }
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

  /**
   * Checks if intersection of `patType` and `scrutineeType` is populated under given `constraints`,
   * that is: for all common base classes `bc` of  `patType` and `scrutineeType`
   * let `btPat`, `btScrutinee` be the base types of `patType`, `scrutineeType` relative to class bc.
   * Then: `btPat` and `btScrutinee` designate to the same class, and
   * any corresponding type arguments of `btPat` and `btScrutinee` are consistent with
   * respect to their variance.
   */
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

  /**
   * Solve accumulated constraints, with respect to `shouldSolveForMaxType` parameter.
   * Type variables in constructor patterns are maximized, type variable in typed patterns
   * are replaced with fresh bounded type parameters, type parameters of the enclosing method
   * are simply solved the usual way.
   */
  private def solve(
    constraints:           ConstraintsResult,
    shouldSolveForMaxType: Boolean,
    tvars:                 Seq[TypeParameter],
    enclosingTypeParams:   Seq[TypeParameter]
  )(implicit
    ctx: ProjectContext
  ): Option[ScSubstitutor] = {
    import ctx.stdTypes

    constraints match {
      case ConstraintsResult.Left => None
      case cs: ConstraintSystem   =>
        if (!shouldSolveForMaxType)
          cs.substitutionBounds(canThrowSCE = false)
            .map { bounds =>
              val tParamSubst =
                ScSubstitutor.bind(enclosingTypeParams)(tParam => {
                  val id = tParam.typeParamId
                  bounds.tvMap.getOrElse(id, TypeParameterType(tParam))
                })

              val tVarSubst =
                ScSubstitutor.bind(tvars)(tvar => {
                  val id = tvar.typeParamId
                  val maybeLower = bounds.lowerMap.get(id)
                  val maybeUpper = bounds.upperMap.get(id)

                  if (maybeLower.isDefined && maybeLower == maybeUpper)
                    maybeLower.get
                  else
                    TypeParameterType(
                      TypeParameter.light(
                        tvar.name,
                        Seq.empty,
                        bounds.lowerMap.getOrElse(id, TypeParameterType(tvar)),
                        bounds.upperMap.getOrElse(id, TypeParameterType(tvar))
                      )
                    )
                })

              tParamSubst.followed(tVarSubst)
            }
        else
          cs.substitutionBounds(canThrowSCE = false)
            .map { bounds =>
              val tParamSubst =
                ScSubstitutor.bind(enclosingTypeParams)(tParam => {
                  val id = tParam.typeParamId
                  bounds.tvMap.getOrElse(id, TypeParameterType(tParam))
                })

              val tVarSubst =
                ScSubstitutor.bind(tvars)(tvar => {
                  val id = tvar.typeParamId
                  bounds.upperMap.getOrElse(id, stdTypes.Any)
                })

              tParamSubst.followed(tVarSubst)
            }
    }
  }

  def doForMatchClause(m: ScMatch, cc: ScCaseClause): ScSubstitutor = {
    (
      for {
        scrutinee    <- m.expression
        scrutineeTpe <- scrutinee.`type`().toOption
        pattern      <- cc.pattern
      } yield PatternTypeInference.doTypeInference(pattern, scrutineeTpe)
    ).getOrElse(ScSubstitutor.empty)
  }
}
