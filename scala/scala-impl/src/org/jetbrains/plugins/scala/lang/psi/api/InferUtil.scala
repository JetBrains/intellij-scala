package org.jetbrains.plugins.scala.lang.psi.api

import com.intellij.openapi.diagnostic.Logger
import com.intellij.psi.search.LocalSearchScope
import com.intellij.psi.search.searches.ClassInheritorsSearch
import com.intellij.psi.{PsiClass, PsiElement}
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.macros.evaluator.{MacroContext, ScalaMacroEvaluator}
import org.jetbrains.plugins.scala.lang.psi.api.base._
import org.jetbrains.plugins.scala.lang.psi.api.expr.{MethodInvocation, ScExpression, ScPostfixExpr}
import org.jetbrains.plugins.scala.lang.psi.api.statements.params.{ScParameter, ScTypeParam, TypeParamIdOwner}
import org.jetbrains.plugins.scala.lang.psi.api.statements.{ScEnumCase, ScExtension, ScFunction, ScTypeAliasDefinition}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.{ScClass, ScEnum, ScObject}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.{ScNamedElement, ScTypedDefinition}
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementFactory
import org.jetbrains.plugins.scala.lang.psi.implicits.ImplicitCollector.ImplicitState
import org.jetbrains.plugins.scala.lang.psi.implicits.{DivergenceChecker, ImplicitCollector}
import org.jetbrains.plugins.scala.lang.psi.light.LightContextFunctionParameter
import org.jetbrains.plugins.scala.lang.psi.types.Compatibility.{ApplicabilityCheckResult, Expression}
import org.jetbrains.plugins.scala.lang.psi.types.ConstraintSystem.SubstitutionBounds
import org.jetbrains.plugins.scala.lang.psi.types._
import org.jetbrains.plugins.scala.lang.psi.types.api._
import org.jetbrains.plugins.scala.lang.psi.types.api.designator.{ScDesignatorType, ScProjectionType, ScThisType}
import org.jetbrains.plugins.scala.lang.psi.types.nonvalue.{Parameter, ScMethodType, ScTypePolymorphicType}
import org.jetbrains.plugins.scala.lang.psi.types.recursiveUpdate.ScSubstitutor
import org.jetbrains.plugins.scala.lang.psi.types.result.Typeable
import org.jetbrains.plugins.scala.lang.psi.{ElementScope, ScalaPsiUtil}
import org.jetbrains.plugins.scala.lang.resolve.ScalaResolveResult
import org.jetbrains.plugins.scala.project._

import scala.annotation.{tailrec, unused}
import scala.collection.immutable.ArraySeq
import scala.util.control.ControlThrowable

object InferUtil {

  val tagsAndManifists: Set[String] = Set(
    "scala.reflect.ClassManifest",
    "scala.reflect.Manifest",
    "scala.reflect.OptManifest",
    "scala.reflect.ClassTag",
    "scala.reflect.api.TypeTags.TypeTag",
    "scala.reflect.api.TypeTags.WeakTypeTag"
  )

  val ValueOf         = "scala.ValueOf"
  val ConformsWitness = "scala.Predef.<:<"
  val EquivWitness    = "scala.Predef.=:="
  val Mirrors         = Seq("scala.deriving.Mirror", "scala.deriving.Mirror.Product", "scala.deriving.Mirror.Sum")

  private val LOG = Logger.getInstance("#org.jetbrains.plugins.scala.lang.psi.api.InferUtil$")

  private def isDebugImplicitParameters = LOG.isDebugEnabled

  @unused
  def logInfo(searchLevel: Int, message: => String): Unit = {
    val indent = Seq.fill(searchLevel)("  ").mkString
    //    println(indent + message)
    if (isDebugImplicitParameters) {
      LOG.debug(indent + message)
    }
  }

  case class ImplicitArgumentsClause(
    args:        Seq[ScalaResolveResult],
    constraints: ConstraintSystem,
    position:    ImplicitClausePosition
  ) {
    def isLeading: Boolean = position == ImplicitClausePosition.Leading
  }

  sealed trait ImplicitClausePosition
  object ImplicitClausePosition {
    case object Leading  extends ImplicitClausePosition
    case object Trailing extends ImplicitClausePosition
  }

  /**
   * Update given method type/context function type by applying it to implicit arguments.
   * Note: it will eagerly apply all encountered using/implicit clauses to arguments.
   *
   * @param tpe MethodType or PolymorphicType(MethodType) to be updated
   * @return    updated type and sequence of implicit parameters
   */
  def updateTypeWithImplicitParameters(
    tpe:                    ScType,
    place:                  PsiElement,
    coreElement:            Option[ScNamedElement],
    canThrowSCE:            Boolean,
    fullInfo:               Boolean,
    throwOnAmbiguous:       Boolean = true,
    implicitRecursionDepth: Int     = 0,
    updateDeep:             Boolean = false,
    isLeadingClause:        Boolean = false
  ): (ScType, Seq[ImplicitArgumentsClause]) = {
    implicit val elementScope: ElementScope = place.elementScope
    implicit val context: Context = Context(place)

    var implicitParameters = Option.empty[Seq[ScalaResolveResult]]
    var updatedType        = tpe
    var constraints        = ConstraintSystem.empty

    tpe.widen match {
      case t @ ScTypePolymorphicType(mt @ ScMethodType(retType, _, isImplicit), _)
        if !isImplicit && updateDeep =>
        // See SCL-3516
        val (updatedReturnType, appliedInner) =
          updateTypeWithImplicitParameters(
            t.copy(internalType = retType),
            place,
            coreElement,
            canThrowSCE,
            fullInfo = fullInfo
          )

        updatedType = updatedReturnType match {
          case tpt: ScTypePolymorphicType =>
            val abstractSubst      = t.abstractOrLowerTypeSubstitutor
            val mtWithoutImplicits = mt.copy(result = tpt.internalType)

            t.copy(
              internalType   = abstractSubst(mtWithoutImplicits),
              typeParameters = tpt.typeParameters
            )
          case _ => //shouldn't be there
            t.copy(
              internalType =
                mt.copy(result = updatedReturnType)
            )
        }
        return (updatedType, appliedInner)
      case ScTypePolymorphicType(internal @ ImplicitMethodOrFunctionType(retType, params), typeParams) =>
        val splitMethodType = internal match {
          case cft @ ContextFunctionType(_, _) => cft
          case mt: ScMethodType =>
            params.reverse.foldLeft(retType) {
              case (tp: ScType, param: Parameter) =>
                ScMethodType(
                  tp,
                  Seq(param),
                  hasImplicitKW = mt.hasImplicitKW,
                  hasUsingKW = mt.hasUsingKW
                )(mt.elementScope)

            }
          case other =>
            throw new IllegalStateException(
              s"Non context-function/method type returned from ImplicitMethodOrFunctionType: $other"
            )
        }

        updatedType = ScTypePolymorphicType(splitMethodType, typeParams)

        val inferredParamsBuffer = ArraySeq.newBuilder[Parameter]
        val exprsBuffer          = ArraySeq.newBuilder[Compatibility.Expression]
        val resolveResultsBuffer = ArraySeq.newBuilder[ScalaResolveResult]

        //todo: do we need to execute this loop several times?
        var i = 0
        while (i < params.size) {
          i += 1
          updatedType match {
            case t @ ScTypePolymorphicType(ImplicitMethodOrFunctionType(retTypeSingle, paramsSingle), typeParamsSingle) =>
              val abstractSubstitutor = t.abstractOrLowerTypeSubstitutor

              val (inferredParams, exprs, resolveResults) =
                findImplicits(
                  paramsSingle,
                  coreElement,
                  place,
                  canThrowSCE,
                  throwOnAmbiguous,
                  implicitRecursionDepth,
                  abstractSubstitutor
                )

              val (updatedWithLocalTypeInference, conformanceResult) =
                localTypeInferenceWithApplicabilityExt(
                  retTypeSingle,
                  inferredParams,
                  exprs,
                  typeParamsSingle,
                  canThrowSCE = canThrowSCE || fullInfo
                )

              updatedType            = updatedWithLocalTypeInference
              constraints           += conformanceResult.constraints
              inferredParamsBuffer ++= inferredParams
              exprsBuffer          ++= exprs
              resolveResultsBuffer ++= resolveResults
          }
        }

        implicitParameters = Option(resolveResultsBuffer.result())
        val dependentSubst = ScSubstitutor.paramToExprType(inferredParamsBuffer.result(), exprsBuffer.result())
        updatedType        = dependentSubst(updatedType)
      case mt @ ScMethodType(retType, _, isImplicit)
        if !isImplicit && updateDeep =>
        // See SCL-3516
        val (updatedReturnType, appliedClauses) =
          updateTypeWithImplicitParameters(
            retType,
            place,
            coreElement,
            canThrowSCE,
            fullInfo = fullInfo
          )

        return (mt.copy(result = updatedReturnType), appliedClauses)
      case ImplicitMethodOrFunctionType(retType, params) =>
        val (inferredParams, exprs, resolveResults) =
          findImplicits(
            params,
            coreElement,
            place,
            canThrowSCE,
            throwOnAmbiguous,
            implicitRecursionDepth
          )

        implicitParameters = Option(resolveResults)
        updatedType        = retType
        val dependentSubst = ScSubstitutor.paramToExprType(inferredParams, exprs)
        updatedType        = dependentSubst(updatedType)
      case _ =>
    }

    implicitParameters match {
      case Some(srrs) =>
        val (resultType, appliedClauses) = updateTypeWithImplicitParameters(
          updatedType,
          place,
          coreElement,
          canThrowSCE,
          throwOnAmbiguous,
          fullInfo,
          implicitRecursionDepth,
          isLeadingClause = isLeadingClause
        )

        val clauseKind =
          if (isLeadingClause) ImplicitClausePosition.Leading
          else                 ImplicitClausePosition.Trailing

        val clause = ImplicitArgumentsClause(srrs, constraints, clauseKind)
        (resultType, clause +: appliedClauses)
      case None =>
        (updatedType, Seq.empty)
    }
  }

  private def findImplicits(
    params:                 Seq[Parameter],
    coreElement:            Option[ScNamedElement],
    place:                  PsiElement,
    canThrowSCE:            Boolean,
    throwOnAmbiguous:       Boolean,
    implicitRecursionDepth: Int           = 0,
    abstractSubstitutor:    ScSubstitutor = ScSubstitutor.empty
  ): (Seq[Parameter], Seq[Compatibility.Expression], Seq[ScalaResolveResult]) = {

    implicit val projectContext: ProjectContext = place.getProject
    implicit val context: Context = Context(place)

    val inferredParams = ArraySeq.newBuilder[Parameter]
    val exprs          = ArraySeq.newBuilder[Compatibility.Expression]
    val resolveResults = ArraySeq.newBuilder[ScalaResolveResult]
    val paramsIterator = params.iterator

    while (paramsIterator.hasNext) {
      val param     = paramsIterator.next()
      val paramType = abstractSubstitutor(param.paramType)

      val implicitState =
        ImplicitState(
          place,
          paramType,
          paramType,
          coreElement,
          isImplicitConversion    = false,
          recursionDepth          = implicitRecursionDepth,
          extensionData           = None,
          fullInfo                = false,
          previousDivergenceStack = Option(DivergenceChecker.currentStack)
        )

      val collector = new ImplicitCollector(implicitState)
      val results   = collector.collect()

      if (results.length == 1) {
        val srr = results.head
        if (canThrowSCE && !srr.isApplicable()) throw new SafeCheckException

        val evaluator = ScalaMacroEvaluator.getInstance(projectContext)

        val resultType =
          evaluator.checkMacro(
            srr.getElement,
            MacroContext(place, Option(paramType))
          ).orElse(
            extractImplicitParameterType(srr)
          )

        inferredParams  += param
        exprs          ++= resultType.map(Expression(_))
        resolveResults  += srr
      } else {
        val compilerGenerated = compilerGeneratedInstance(paramType)

        val result = compilerGenerated.getOrElse {
          if (param.isDefault && param.paramInCode.nonEmpty) {
            new ScalaResolveResult(param.paramInCode.get)
          } else if (canThrowSCE && (throwOnAmbiguous || results.isEmpty))  {
            throw new SafeCheckException
          } else {
            val problem =
              if (results.isEmpty)
                NotFoundImplicitParameter(paramType)
              else
                AmbiguousImplicitParameters(results)

            val psiParam = param.paramInCode.getOrElse(
              ScalaPsiElementFactory.createParameterFromText(
                param.name + " : Int",
                place
              )
            )

            new ScalaResolveResult(
              psiParam,
              problems            = Seq(problem),
              implicitSearchState = Option(implicitState)
            )
          }
        }

        resolveResults += result
      }
    }

    (inferredParams.result(), exprs.result(), resolveResults.result())
  }

  private def compilerGeneratedInstance(tp: ScType)(implicit context: Context): Option[ScalaResolveResult] =
    tp.removeAliasDefinitions() match {
      case p @ ParameterizedType(_, params) =>
        p.extractClass.collect {
          case clazz if areEligible(params, clazz.qualifiedName) =>
            new ScalaResolveResult(clazz, p.substitutor)
        }
      case ScCompoundType(Seq(ExtractClass(cls)), _, typesMap) if Mirrors.contains(cls.qualifiedName) =>
        typesMap
          .get("MirroredMonoType")
          .map(sig => sig.typeAlias -> sig.substitutor)
          .collect {
            case (tdef: ScTypeAliasDefinition, subst) if !tdef.isEffectivelyOpaque && eligibleForMirror(subst(tdef.aliasedType.getOrAny)) =>
              new ScalaResolveResult(cls)
          }
      case _ => None
    }


  private def areEligible(params: Seq[ScType], typeFqn: String)(implicit context: Context): Boolean =
    (typeFqn, params) match {
      case (ValueOf, Seq(t))                            => eligibleForValueOf(t)
      case (ConformsWitness, Seq(t1, t2))               => t1.conforms(t2)
      case (EquivWitness, Seq(t1, t2))                  => t1.equiv(t2)
      case (mirror, Seq(t)) if Mirrors.contains(mirror) => eligibleForMirror(t)
      case _ if params.size == 1                        => tagsAndManifists.contains(typeFqn)
      case _                                            => false
    }

  private def eligibleForMirror(tpe: ScType)(implicit context: Context): Boolean = {
    tpe.extractDesignated(expandAliases = true) match {
      case Some(des) => des match {
        case obj: ScObject                   => obj.isCase
        case _: ScEnum                       => true
        case _: ScEnumCase                   => true
        case cls: ScClass if cls.isCase      => true
        case tdef: PsiClass if tdef.isSealed =>
          ClassInheritorsSearch.search(
            tdef,
            new LocalSearchScope(tdef.getContainingFile),
            true
          ).allMatch(cls => eligibleForMirror(ScDesignatorType(cls)))
        case _ => false
      }
      case _ => false
    }
  }

  private def eligibleForValueOf(t: ScType)(implicit context: Context): Boolean = {
    t.removeAliasDefinitions().inferValueType match {
      case _: ScLiteralType         => true
      case _ if t.isUnit            => true
      case _: ScThisType            => true
      case tpt: TypeParameterType   => eligibleForValueOf(tpt.upperType)
      case ScCompoundType(cs, _, _) => cs.exists(eligibleForValueOf)
      case valueType                => isStable(valueType)
    }
  }

  private def isStable(t: ScType): Boolean = {
    val designator = t match {
      case ScProjectionType(_, td: ScTypedDefinition) => Some(td)
      case ScDesignatorType(td: ScTypedDefinition)    => Some(td)
      case _ => None
    }
    designator.exists(d => d.isStable && ScalaPsiUtil.hasStablePath(d))
  }

  /**
   * Updates polymorphic type according to `expectedType`
   */
  def updateAccordingToExpectedType(
    _nonValueType:    ScType,
    filterTypeParams: Boolean,
    expectedType:     Option[ScType],
    expr:             PsiElement,
    canThrowSCE:      Boolean
  ): ScType = {
    implicit val projectContext: ProjectContext = expr
    implicit val context: Context = Context(expr)

    val Unit = projectContext.stdTypes.Unit

    val shouldTruncateImplicitParameters = expectedType match {
      case Some(ContextFunctionType(_, _)) => false
      case _                               => true
    }

    val ptUnwrapped = expectedType match {
      case Some(ContextFunctionType(retTpe, _)) => retTpe.toOption
      case other                                => other
    }

    @tailrec
    def shouldSearchImplicit(t: ScType, ptConstraints: ConstraintSystem, first: Boolean = true): Boolean = t match {
      case ScMethodType(_, params, isImplicit) if isImplicit =>
        !first &&  // implicit method type on top level means explicit implicit argument
          params.forall(p => p.paramType.subtypeExists {
            case tpt: TypeParameterType => ptConstraints.isApplicable(tpt.typeParamId)
            case _                      => false
          })
      case ScTypePolymorphicType(internalType, _) => shouldSearchImplicit(internalType, ptConstraints, first = first)
      case ScMethodType(returnType, _, _)         => shouldSearchImplicit(returnType, ptConstraints, first = false)
      case _                                      => false
    }

    def implicitSearchFails(tp: ScType): Boolean = expr match {
      case e: ScExpression =>
        val appliedClauses = e.updatedWithImplicitArguments(tp, checkExpectedType = false, updateDeep = false)._2
        appliedClauses.exists { _.args.exists {
          case srr if srr.isNotFoundImplicitParameter  => true
          case srr if srr.isAmbiguousImplicitParameter =>
            // we found several implicits, but not all type parameters are fully inferred yet, it may be fine
            tp.asOptionOf[ScTypePolymorphicType].exists(_.typeParameters.isEmpty)
          case _                                       => false
        }
        }
      case _ => false
    }

    def cantFindImplicitsFor(tp: ScType, ptConstraints: ConstraintSystem): Boolean =
      shouldSearchImplicit(tp, ptConstraints) && implicitSearchFails(tp)

    def doLocalTypeInference(tpt: ScTypePolymorphicType, expected: ScType): ScType = {
      val ScTypePolymorphicType(internal, typeParams) = tpt

      val sameDepth = internal match {
        case m: ScMethodType => truncateMethodType(m, expr, shouldTruncateImplicitParameters)
        case _               => internal
      }

      val valueType          = sameDepth.inferValueType
      val expectedParam      = Parameter("", None, expected, expected)
      val expressionToUpdate = Expression(ScSubstitutor.bind(typeParams)(UndefinedType(_)).apply(valueType))

      val (inferredWithExpected, conformanceResult) =
        localTypeInferenceWithApplicabilityExt(
          internal,
          Seq(expectedParam),
          Seq(expressionToUpdate),
          typeParams,
          shouldUndefineParameters = false,
          canThrowSCE              = canThrowSCE,
          filterTypeParams         = filterTypeParams
        )

      val subst =
        if (!filterTypeParams) {
          val fullyInferedTypeParameters =
            inferredWithExpected
              .typeParameters
              .filter(p => p.lowerType.equiv(p.upperType))

          ScSubstitutor.bind(fullyInferedTypeParameters)(_.lowerType)
        } else ScSubstitutor.empty

      val result = subst(inferredWithExpected)

      /** See
       * [[scala.tools.nsc.typechecker.Typers.Typer.adapt#adaptToImplicitMethod]]
       *
       * If there is not found implicit for type parameters inferred using expected type,
       * rollback type inference, it may be fixed later with implicit conversion
       */
      if (cantFindImplicitsFor(result, conformanceResult.constraints)) _nonValueType
      else                                                             result
    }

    val nonValueType = (_nonValueType, ptUnwrapped) match {
      case (tpt: ScTypePolymorphicType, Some(expected)) if !expected.equiv(Unit) =>
        doLocalTypeInference(tpt, expected)
      case _                                                                     =>
        _nonValueType
    }

    if (!expr.is[ScExpression])
      return nonValueType

    // interim fix for SCL-3905.
    def applyImplicitViewToResult(
      mt: ScMethodType,
      expectedType: Option[ScType],
      fromSAM: Boolean = false,
      fromMethodInvocation: Boolean = false
    ): ScMethodType = {
      implicit val elementScope: ElementScope = mt.elementScope
      val ScMethodType(result, params, _) = mt

      expr match {
        case _: MethodInvocation if !fromMethodInvocation =>
          result match {
            case methodType: ScMethodType =>
              val resultNew = applyImplicitViewToResult(methodType, expectedType, fromSAM, fromMethodInvocation = true)
              mt.copy(result = resultNew)
            case _ => mt
          }
        case _ =>
          expectedType match {
            case Some(expected) if result.conforms(expected) => mt
            case Some(FunctionType(expectedRet, expectedParams)) if expectedParams.length == params.length =>
              if (expectedRet.equiv(Unit)) { //value discarding
                mt.copy(result = Unit)
              }
              else {
                result match {
                  case methodType: ScMethodType =>
                    val resultNew = applyImplicitViewToResult(methodType, Some(expectedRet), fromSAM)
                    return mt.copy(result = resultNew)
                  case _ =>
                }

                import literals.ScNullLiteral
                val nullLiteral = ScalaPsiElementFactory.createExpressionWithContextFromText(
                  "null",
                  expr.getContext,
                  expr
                ).asInstanceOf[ScNullLiteral]
                ScNullLiteral(nullLiteral) = result

                val updatedResultType = nullLiteral.getTypeAfterImplicitConversion(expectedOption = Some(expectedRet))

                expr.asInstanceOf[ScExpression].setAdditionalExpression(Some(nullLiteral, expectedRet))

                mt.copy(result = updatedResultType.tr.getOrElse(result))
              }
            case _ => mt
          }
      }
    }

    nonValueType match {
      case tpt@ScTypePolymorphicType(mt: ScMethodType, _) =>
        val canConform = if (!filterTypeParams) {
          val subst         = tpt.abstractTypeSubstitutor
          val withAbstracts = subst(mt).asInstanceOf[ScMethodType]
          truncateMethodType(withAbstracts, expr, shouldTruncateImplicitParameters)
        } else truncateMethodType(mt, expr, shouldTruncateImplicitParameters)

        if (ptUnwrapped.forall(canConform.conforms)) tpt
        else tpt.copy(internalType = applyImplicitViewToResult(mt, ptUnwrapped))
      case mt: ScMethodType =>
        applyImplicitViewToResult(mt, ptUnwrapped)
      case t => t
    }
  }

  //truncate method type to have a chance to conform to expected
  private[this] def truncateMethodType(
    tpe:                              ScType,
    expr:                             PsiElement,
    shouldTruncateImplicitParameters: Boolean
  ): ScType = {
    def withoutImplicitClause(internal: ScType): ScType = if (shouldTruncateImplicitParameters) {
      internal match {
        case ScMethodType(retType, _, true) => retType
        case m @ ScMethodType(retType, params, false) =>
          ScMethodType(withoutImplicitClause(retType), params)(m.elementScope)
        case other => other
      }
    } else internal

    @tailrec
    def countParameterLists(invocation: MethodInvocation, acc: Int = 1): Int =
      invocation.getEffectiveInvokedExpr match {
        case inv: MethodInvocation => countParameterLists(inv, acc + 1)
        case _                     => acc
      }

    @tailrec
    def removeNComponents(tp: ScType, n: Int): ScType = tp match {
      case ScMethodType(resTpe, _, _) if n > 0 => removeNComponents(resTpe, n - 1)
      case _                                   => tp
    }

    val withoutImplicits = withoutImplicitClause(tpe)
    expr match {
      case _: ScPostfixExpr =>
        withoutImplicits //SCL-17198
      case inv: MethodInvocation =>
        removeNComponents(withoutImplicits, countParameterLists(inv))
      case _ =>
        withoutImplicits
    }
  }

  def extractImplicitParameterType(result: ScalaResolveResult): Option[ScType] =
    result.implicitResultType.orElse {
      val ScalaResolveResult(element, substitutor) = result

      val maybeType = element match {
        case lightParam: LightContextFunctionParameter =>
          lightParam.contextFunctionParameterType.toOption
        case _: ScObject |
             _: ScParameter |
             _: patterns.ScBindingPattern |
             _: ScFieldId => element.asInstanceOf[Typeable].`type`().toOption
        case function: ScFunction =>
          val extensionOwner = result.exportedInExtension
          functionTypeNoImplicits(function, extensionOwner)
      }

      maybeType.map(substitutor)
    }

  def localTypeInference(
    retType:                  ScType,
    params:                   Seq[Parameter],
    exprs:                    Seq[Expression],
    typeParams:               Seq[TypeParameter],
    shouldUndefineParameters: Boolean = true,
    canThrowSCE:              Boolean = false,
    filterTypeParams:         Boolean = true
  )(implicit context: Context): ScTypePolymorphicType =
    localTypeInferenceWithApplicabilityExt(
      retType,
      params,
      exprs,
      typeParams,
      shouldUndefineParameters,
      canThrowSCE,
      filterTypeParams
    )._1

  class SafeCheckException extends ControlThrowable

  def localTypeInferenceWithApplicabilityExt(
    retType:                  ScType,
    params:                   Seq[Parameter],
    exprs:                    Seq[Expression],
    typeParams:               Seq[TypeParameter],
    shouldUndefineParameters: Boolean               = true,
    canThrowSCE:              Boolean               = false,
    filterTypeParams:         Boolean               = true,
    paramSubst:               Option[ScSubstitutor] = None
  )(implicit context: Context): (ScTypePolymorphicType, ApplicabilityCheckResult) = {
    implicit val projectContext: ProjectContext = retType.projectContext

    val typeParamIds = typeParams.map(_.typeParamId).toSet
    def hasRecursiveTypeParams(tpe: ScType): Boolean = tpe.hasRecursiveTypeParameters(typeParamIds)

    // See SCL-3052, SCL-3058
    // This corresponds to use of `isCompatible` in `Infer#methTypeArgs` in scalac, where `isCompatible` uses `weak_<:<`
    val undefSubst: ScSubstitutor =
      if (shouldUndefineParameters) ScSubstitutor.bind(typeParams)(UndefinedType(_))
      else                          ScSubstitutor.empty

    val eTpeSubst = paramSubst.getOrElse(
      ScTypePolymorphicType(retType, typeParams).abstractTypeSubstitutor
    )

    val paramsWithUndefTypes = params.map(
      p =>
        p.copy(
          paramType    = undefSubst(p.paramType),
          expectedType = eTpeSubst(p.paramType),
          defaultType  = p.defaultType.map(undefSubst)
        )
    )

    val conformanceResult @ ApplicabilityCheckResult(problems, constraints, _, _) =
      Compatibility.checkMethodApplicability(
        paramsWithUndefTypes,
        exprs,
        withImplicits = true,
        shapesOnly    = false
      )

    val tpe = if (problems.isEmpty) {
      constraints.substitutionBounds(canThrowSCE) match {
        case Some(bounds @ SubstitutionBounds(_, lowerMap, upperMap)) =>
          val unSubst = bounds.substitutor
          if (!filterTypeParams) {

            def combineBounds(tp: TypeParameter, isLower: Boolean): ScType = {
              val bound        = if (isLower) tp.lowerType else tp.upperType
              val substedBound = unSubst(bound)
              val boundsMap    = if (isLower) lowerMap else upperMap

              val combine: (ScType, ScType) => ScType = if (isLower) _ lub _ else _ glb _

              boundsMap.get(tp.typeParamId) match {
                case Some(fromMap) =>
                  val mayCombine = !substedBound.equiv(fromMap) &&
                    !substedBound.hasRecursiveTypeParameters(Set(tp.typeParamId))

                  if (mayCombine) combine(substedBound, fromMap)
                  else            fromMap
                case _ => substedBound
              }
            }

            val undefiningSubstitutor = ScSubstitutor.bind(typeParams)(UndefinedType(_))

            ScTypePolymorphicType(retType, typeParams.map { tp =>
              val lower = combineBounds(tp, isLower = true)
              val upper = combineBounds(tp, isLower = false)

              val boundsConformanceCheck =
                undefiningSubstitutor(lower).conforms(
                  undefiningSubstitutor(upper),
                  ConstraintSystem.empty,
                  checkWeak = true
                )

              if (canThrowSCE && !boundsConformanceCheck.isRight)
                throw new SafeCheckException

              TypeParameter(
                tp.psiTypeParameter, /* doesn't important here */
                tp.typeParameters,
                lower,
                upper
              )
            })
          } else {

            def addConstraints(un: ConstraintSystem, tp: TypeParameter): ConstraintSystem = {
              val typeParamId  = tp.typeParamId
              val substedLower = unSubst(tp.lowerType)
              val substedUpper = unSubst(tp.upperType)

              var result = un

              if (un.isApplicable(typeParamId) || substedLower != Nothing) {
                //todo: add only one of them according to variance

                //add constraints for tp from its' bounds
                if (!substedLower.isNothing && !hasRecursiveTypeParams(substedLower)) {
                  result = result.withLower(typeParamId, substedLower)
                    .withTypeParamId(typeParamId)
                }
                if (!substedUpper.isAny && !hasRecursiveTypeParams(substedUpper)) {
                  result = result.withUpper(typeParamId, substedUpper)
                    .withTypeParamId(typeParamId)
                }

                val lowerTpId = substedLower.asOptionOf[TypeParameterType].map(_.typeParamId).filter(typeParamIds.contains)
                val upperTpId = substedUpper.asOptionOf[TypeParameterType].map(_.typeParamId).filter(typeParamIds.contains)

                val substedTp = unSubst(TypeParameterType(tp))

                //add constraints for tp bounds from tp substitution
                if (!hasRecursiveTypeParams(substedTp)) {
                  upperTpId.foreach { id =>
                    result = result.withLower(id, substedTp)
                      .withTypeParamId(id)
                  }
                  lowerTpId.foreach { id =>
                    result = result.withUpper(id, substedTp)
                      .withTypeParamId(id)
                  }
                }
              }

              result
            }

            val newConstraints = typeParams.foldLeft(constraints)(addConstraints)

            val notInferred =
              if (!retType.isValue) Seq.empty
              else
                typeParams.filter(tp =>
                  tp.varianceInType(retType).isContravariant &&
                    !newConstraints.isApplicable(tp.typeParamId)
                )

            val contrSubst = ScSubstitutor.bind(notInferred)(tp => unSubst(tp.upperType))

            import org.jetbrains.plugins.scala.lang.psi.types.recursiveUpdate.SubtypeUpdater._

            def updateWithSubst(sub: ScSubstitutor): ScTypePolymorphicType = ScTypePolymorphicType(
              sub(retType),
              typeParams.filter { tp =>
                val removeMe = newConstraints.isApplicable(tp.typeParamId)

                if (removeMe && canThrowSCE) {
                  tp.psiTypeParameter match {
                    case typeParam: ScTypeParam =>
                      val tpt     = TypeParameterType(typeParam)
                      val substed = sub(tpt)

                      val kindsMatch =
                        tpt.typeParameters.isEmpty ||
                          substed.isAny ||
                          TypeVariableUnification.unifiableKinds(tpt, substed)

                      if (!kindsMatch) throw new SafeCheckException
                    case _ => ()
                  }
                }
                !removeMe
              }.map(_.update(sub))
            )

            newConstraints match {
              case ConstraintSystem(substitutor) => updateWithSubst(substitutor.followed(contrSubst))
              case _ if !canThrowSCE             => updateWithSubst(unSubst.followed(contrSubst))
              case _                             => throw new SafeCheckException
            }
          }
        case None => throw new SafeCheckException
      }
    } else ScTypePolymorphicType(retType, typeParams)
    (tpe, conformanceResult)
  }

  def functionTypeNoImplicits(function: ScFunction, extensionOwner: Option[ScExtension] = None): Option[ScType] = {
    val retType = function.returnType.toOption

    collectReverseParamTypesNoImplicits(function, extensionOwner).flatMap {
      params =>
        implicit val scope: ElementScope = ElementScope(function)
        retType.map(params.foldLeft(_)((res, params) => FunctionType(res, params)))
    }
  }

  private def collectReverseParamTypesNoImplicits(
    function:       ScFunction,
    extensionOwner: Option[ScExtension] = None
  ): Option[Seq[Seq[ScType]]] = {
    val builder = Seq.newBuilder[Seq[ScType]]
    val owner   = extensionOwner.orElse(function.extensionMethodOwner)

    //Two cases:
    //1. implicit def foo(x: Foo)(using Bar): Baz = ???
    //   simply drop implicit/using clauses, result: Foo => Baz
    //2. extension (using Bar)(x: Foo)(using Baz) { def foo(x: Int)(using Qux): String = ??? }
    //   drop implicit/using clauses from the extension itself, leave target method untouched
    //   result: Foo => Int => using Qux => String
    val clauses = owner match {
      case Some(ext) =>
        ext.effectiveParameterClauses.filterNot(_.isImplicit) ++
          function.effectiveParameterClauses
      case None => function.effectiveParameterClauses.filterNot(_.isImplicit)
    }

    //for performance
    var idx = clauses.length - 1
    while (idx >= 0) {
      val cl         = clauses(idx)
      val parameters = cl.parameters
      val paramTypes = parameters.flatMap(_.`type`().toOption)

      if (paramTypes.size != parameters.size) return None
      else                                    builder += paramTypes
      idx -= 1
    }

    Option(builder.result())
  }
}
