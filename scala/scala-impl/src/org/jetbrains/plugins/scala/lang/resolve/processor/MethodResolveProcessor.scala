package org.jetbrains.plugins.scala.lang.resolve.processor

import com.intellij.psi._
import com.intellij.psi.impl.light.LightDefaultConstructor
import org.jetbrains.plugins.scala.ScalaBundle
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiUtil
import org.jetbrains.plugins.scala.lang.psi.api.base._
import org.jetbrains.plugins.scala.lang.psi.api.base.types.ScTypeArgument
import org.jetbrains.plugins.scala.lang.psi.api.expr._
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScFunction.CommonNames
import org.jetbrains.plugins.scala.lang.psi.api.statements._
import org.jetbrains.plugins.scala.lang.psi.api.statements.params.TypeParamIdOwner
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.{ScObject, ScTypeDefinition}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.{ScTypeParametersOwner, ScTypedDefinition}
import org.jetbrains.plugins.scala.lang.psi.impl.ScPackageImpl
import org.jetbrains.plugins.scala.lang.psi.impl.toplevel.synthetic.ScSyntheticFunction
import org.jetbrains.plugins.scala.lang.psi.types.Compatibility.{ApplicabilityCheckResult, Expression}
import org.jetbrains.plugins.scala.lang.psi.types._
import org.jetbrains.plugins.scala.lang.psi.types.api._
import org.jetbrains.plugins.scala.lang.psi.types.api.designator.{ScDesignatorType, ScProjectionType}
import org.jetbrains.plugins.scala.lang.psi.types.nonvalue.{ScMethodType, ScTypePolymorphicType}
import org.jetbrains.plugins.scala.lang.psi.types.recursiveUpdate.ScSubstitutor
import org.jetbrains.plugins.scala.lang.psi.types.result._
import org.jetbrains.plugins.scala.lang.resolve.MethodTypeProvider._
import org.jetbrains.plugins.scala.lang.resolve.ResolveUtils.PsiElementForExpectedTypesEx
import org.jetbrains.plugins.scala.lang.resolve.ScalaResolveState.ResolveStateExt
import org.jetbrains.plugins.scala.lang.resolve.processor.MethodResolveProcessor.InvocationClause
import org.jetbrains.plugins.scala.lang.resolve.{ResolveTargets, ScalaResolveResult, StdKinds}
import org.jetbrains.plugins.scala.project.{ProjectContext, ProjectPsiElementExt}
import org.jetbrains.plugins.scala.util.SAMUtil

import scala.annotation.tailrec

class MethodResolveProcessor(
  override val ref:           PsiElement,
  val refName:                String,
  val invocationClauses:      Seq[InvocationClause],
  val prevTypeInfo:           Seq[TypeParameter],
  override val kinds:         Set[ResolveTargets.Value] = StdKinds.methodRef,
  val expectedOption:         () => Option[ScType]      = () => None,
  val isUnderscore:           Boolean                   = false,
  var isShapeResolve:         Boolean                   = false,
  val constructorResolve:     Boolean                   = false,
  val enableTupling:          Boolean                   = false,
  val noImplicitsForArgs:     Boolean                   = false,
  val selfConstructorResolve: Boolean                   = false,
  val nameArgForDynamic:      Option[String]            = None
) extends ResolveProcessor(kinds, ref, refName) {

  private def typeArgsForArgClause(argClauseIdx: Int): Seq[ScTypeArgument] =
    invocationClauses.lift(argClauseIdx).map(
      _.targs.getOrElse(Seq.empty)
    ).getOrElse(Seq.empty)

  private def valueArgsForArgClause(argClauseIdx: Int): Option[Seq[Expression]] =
    invocationClauses.lift(argClauseIdx).flatMap(_.args)

  def copy(
    ref:                    PsiElement                = ref,
    refName:                String                    = refName,
    invocationClauses:      Seq[InvocationClause]     = invocationClauses,
    prevTypeInfo:           Seq[TypeParameter]        = prevTypeInfo,
    kinds:                  Set[ResolveTargets.Value] = kinds,
    expectedOption:         () => Option[ScType]      = expectedOption,
    isUnderscore:           Boolean                   = isUnderscore,
    isShapeResolve:         Boolean                   = isShapeResolve,
    constructorResolve:     Boolean                   = constructorResolve,
    enableTupling:          Boolean                   = enableTupling,
    noImplicitsForArgs:     Boolean                   = noImplicitsForArgs,
    selfConstructorResolve: Boolean                   = selfConstructorResolve,
    nameArgForDynamic:      Option[String]            = nameArgForDynamic
  ): MethodResolveProcessor = new MethodResolveProcessor(
    ref,
    refName,
    invocationClauses,
    prevTypeInfo,
    kinds,
    expectedOption,
    isUnderscore,
    isShapeResolve,
    constructorResolve,
    enableTupling,
    noImplicitsForArgs,
    selfConstructorResolve,
    nameArgForDynamic
  )

  private def isDynamic: Boolean                 = nameArgForDynamic.nonEmpty
  private def useScala3OverloadingRules: Boolean = ref.isInScala3File

  override protected def execute(namedElement: PsiNamedElement)
                                (implicit state: ResolveState): Boolean = {

    def implFunction: Option[ScalaResolveResult]             = state.implicitConversion
    def implType: Option[ScType]                             = state.implicitConversionResultType
    def implScopeType: Option[ScType]                        = state.implicitScopeType
    def isExtensionFromGiven: Boolean                        = state.isExtensionFromGiven
    def isNamedParameter: Boolean                            = state.isNamedParameter
    def fromType: Option[ScType]                             = state.fromType
    def unresolvedTypeParameters: Option[Seq[TypeParameter]] = state.unresolvedTypeParams
    def renamed: Option[String]                              = state.renamed
    def forwardReference: Boolean                            = state.isForwardRef
    def extensionMethod: Boolean                             = state.isExtensionMethod
    def extensionContext: Option[ScExtension]                = state.extensionContext
    def intersectedReturnType: Option[ScType]                = state.intersectedReturnType
    def importsUsed                                          = state.importsUsed
    def exportedInfo                                         = state.exportedInfo

    if (nameMatches(namedElement) || constructorResolve) {
      val accessible = isNamedParameter || isAccessible(namedElement, ref)
      if (accessibility && !accessible) return true

      val s =
        state.substitutorWithThisType(namedElement.findContextOfType(classOf[PsiClass]).orNull)

      val resultBuilder: PsiNamedElement => ScalaResolveResult = e =>
        new ScalaResolveResult(
          e,
          s,
          importsUsed,
          renamed,
          implicitConversion             = implFunction,
          implicitConversionResultType   = implType,
          fromType                       = fromType,
          isNamedParameter               = isNamedParameter,
          isAccessible                   = accessible,
          isForwardReference             = forwardReference,
          unresolvedTypeParameters       = unresolvedTypeParameters,
          isExtensionCall                = extensionMethod,
          extensionContext               = extensionContext,
          matchClauseSubstitutor         = state.matchClauseSubstitutor,
          intersectedReturnType          = intersectedReturnType,
          exportedInfo                   = exportedInfo,
          implicitScopeType              = implScopeType,
          isExtensionFromGiven           = isExtensionFromGiven,
        )

      namedElement match {
        case m: PsiMethod                                                   => addResult(resultBuilder(m))
        case o: ScObject if o.isPackageObject                               =>  // do not resolve to package object
        case obj: ScObject if ref.getParent.is[ScMethodCall, ScGenericCall] => addResult(resultBuilder(obj))
        case target @ (_: PsiClass | _: ScTypeAliasDefinition)
          if ref.isInScala3Module && ref.getParent.is[ScMethodCall, ScGenericCall] =>

          val targetCls = target match {
            case alias: ScTypeAliasDefinition =>
              val rhsOption = alias.aliasedType.toOption
              rhsOption.flatMap(_.extractClass)
            case cls: PsiClass                => Option(cls)
            case _                            => throw new IllegalArgumentException(ScalaBundle.message("unexpected.resolve.target", target))
          }

          targetCls.foreach { cls =>
            // process constructor proxies
            val constructors = cls.constructors match {
              case Seq() =>
                LightDefaultConstructor.create(cls).toOption.toSeq
              case other => other
            }

            val withAccessibilityCheck =
              constructors.view.map { cons =>
                new ScalaResolveResult(
                  cons,
                  ScSubstitutor.empty,
                  importsUsed,
                  renamed,
                  implicitConversion           = implFunction,
                  implicitConversionResultType = implType,
                  fromType                     = fromType,
                  parentElement                = Option(cls),
                  isAccessible                 = isAccessible(cons, ref),
                  isForwardReference           = forwardReference
                )
              }.filter(srr => !accessibility || srr.isAccessible)

            addResults(withAccessibilityCheck)
          }
        case synthetic: ScSyntheticFunction => addResult(resultBuilder(synthetic))
        case pack: PsiPackage               => addResult(resultBuilder(ScPackageImpl(pack)))
        case _                              => addResult(resultBuilder(namedElement))
      }
    }
    true
  }

  override def candidatesS: Set[ScalaResolveResult] =
    if (isDynamic) {
      collectCandidates(
        super.candidatesS.collect {
          case srr if srr.isApplicable() => srr.copy(nameArgForDynamic = nameArgForDynamic)
        }
      )
    } else {
      val superCandidates = super.candidatesS
      collectCandidates(superCandidates)
    }

  private def collectCandidates(input: Set[ScalaResolveResult]): Set[ScalaResolveResult] =
    if (input.isEmpty) input
    else {
      MethodResolveProcessor.candidates(this, input)
    }
}

object MethodResolveProcessor {
  private def updateCandidateWithApplicabilityProblems(
    place:                  PsiElement,
    candidate:              ApplicabilityCandidate,
    checkArgsWithImplicits: Boolean,
    allArgumentClauses:     Seq[Seq[Expression]],
    currentTypeArgsClause:  Seq[ScTypeArgument],
    currentArgumentsClause: Option[Seq[Expression]],
    prevTypeInfo:           Seq[TypeParameter],
    expectedOption:         () => Option[ScType],
    selfConstructorResolve: Boolean,
    isUnderscore:           Boolean,
    shapesOnly:             Boolean,
    argClauseIdx:           Int,
    isLastClause:           Boolean
  ): ApplicabilityCandidate = {
    val srr = candidate.resolveResult

    //Apply/update candidates have already went through applicability checks, no need
    //to run them again. Note: only true for scala 2, where overloading resolution only checks the first argument clause,
    //this can lead to incorrect results, in scala 3, e.g. when we have multiple overloaded expanded apply
    //alternatives differing in (n+1)th arg clause.
    val isExpandedApplyOrUpdateCandidate = candidate.shift != 0
    if (isExpandedApplyOrUpdateCandidate && !place.isInScala3File) return candidate

    //An exotic case, where first apply method expansion happens and subsequently
    //*Dynamic method is resolved.
    //This is not entirely correct, but I don't think it is worth implementing fully.
    if (srr.nameArgForDynamic.contains(CommonNames.Apply)) return candidate

    val clauseIdx     = candidate.effectiveClauseIdx(argClauseIdx)
    val effectiveArgs = allArgumentClauses.drop(candidate.shift)

    implicit val projectContext: ProjectContext = srr.element
    implicit val context: Context               = Context(place)

    val problems             = Seq.newBuilder[ApplicabilityProblem]
    val element              = srr.element
    val candidateSubstitutor = srr.substitutor

    val typeParametersForIdx =
      typeParametersForArgClause(
        element,
        clauseIdx,
        srr.isExtensionCall,
        srr.exportedInExtension,
        effectiveArgs
      ).getOrElse(Seq.empty)

    val typeParamsSize = typeParametersForIdx.size

    //This dichotomy between class type parameters and getConstructorTypeParameters can be somewhat hard to follow:
    //1. The initial ScalaResolveResult (and subsequently its substitutor) is formulated in terms of class type parameters
    //2. This becomes somewhat problematic when we call constructor from inside the class which defines it, e.g.
    //   `class Foo[A <: Bar](a: A) { def foo: Foo[A] = new Foo[A](???)`, here we would have to solve for A <: (Bar, A) >: A
    //3. So to avoid that, let's operate here entirely in terms of synthetic constructor type parameters
    //Also, the order of substitutors matter. Should be srr.substitutor -> bindConstructorTypeParams -> everything else.
    val bindConstructorTypeParamsSubst = element match {
      case cons @ ScalaConstructor.in(td: ScTypeDefinition) =>
        val maybeAlias = srr.parentElement.collect { case alias: ScTypeAliasDefinition => alias }
        val typeParams = maybeAlias.fold(td.typeParameters)(_.typeParameters)
        ScSubstitutor.bind(typeParams, cons.getConstructorTypeParameters)(TypeParameterType(_))
      case _ => ScSubstitutor.empty
    }

    val currentClauseTypeParamsSubst =
      undefinedOrTypeArgsSubstitutor(
        candidateSubstitutor.followed(bindConstructorTypeParamsSubst),
        selfConstructorResolve,
        typeParametersForIdx,
        currentTypeArgsClause
      )

    //@TODO: def foo[A](a: A)(b: A); foo(1)("2") is valid scala 3, but not 2.
    //       This is out-of-scope of the interleaved param. clauses ticket,
    //       so I'll just leave it for now.
    val typeParamsFromPrevClauses = candidate.processedTypeParams

    //all type parameters, not directly related to the current invocation clause
    val previousTypeParams =
      prevTypeInfo ++
        srr.unresolvedTypeParameters.getOrElse(Seq.empty) ++
        typeParamsFromPrevClauses

    val undefSubst = ScSubstitutor.bind(previousTypeParams)(UndefinedType(_))

    val substitutor = currentClauseTypeParamsSubst.followed(undefSubst)

    val allTypeParameters = previousTypeParams ++ typeParametersForIdx

    def addExpectedTypeProblems(): ApplicabilityCheckResult = {
      if (expectedOption().isEmpty || !isLastClause) {
        val problemsSeq = problems.result()
        return ApplicabilityCheckResult(problemsSeq)
      }

      val expected = expectedOption().get

      val retType: ScType = element match {
        case cons @ ScalaConstructor.in(td: ScTypeDefinition) =>
          val bindTypeParamsSubst = ScSubstitutor.bind(td.typeParameters, cons.getConstructorTypeParameters)(TypeParameterType(_))
          substitutor(bindTypeParamsSubst(td.`type`().getOrNothing))
        case Constructor.ofClass(cls) =>
          substitutor(ScalaPsiUtil.constructTypeForPsiClass(cls)((tp, _) => TypeParameterType(tp)))
        case f: ScFunction if !f.isInScala3File && f.paramClauses.clauses.count(!_.isImplicit) > effectiveArgs.size =>
          problems += ExpectedTypeMismatch //do not check expected types for more than one param clauses
          Nothing
        case f: ScFunction          => substitutor(f.returnType.getOrNothing)
        case f: ScSyntheticFunction => substitutor(f.retType)
        case m: PsiMethod  =>
          Option(m.getReturnType)
            .map(rt => substitutor(rt.toScType()))
            .getOrElse(Nothing)
        case _ => Nothing
      }

      val conformance = retType.typeSystem.conformsInner(expected, retType)
      if (conformance.isLeft && !expected.equiv(api.Unit)) {
        problems += ExpectedTypeMismatch
      }

      ApplicabilityCheckResult(problems.result(), conformance.constraints)
    }

    /**
     * Precondition: [[currentArgumentsClause]] is empty.
     */
    def checkFunctionReference(fun: PsiNamedElement, isPolymorphic: Boolean): ApplicabilityCheckResult = {
      def default(): ApplicabilityCheckResult = {
        val canBeNullaryMethodApplication = {
          //We can only invoke empty-paren methods as parameterless
          // 1. in scala 2 (where it is a compiler warning)
          // 2. if it is a Java method (defined in java or overriding one)
          !fun.isInScala3File ||  // 1.
            fun
              .asOptionOf[ScFunction]
              .forall(
                _.superMethods.exists(
                  !_.is[ScFunction] // 2.
                )
              )
        }

        fun match {
          case fn: ScFunction
            if srr.functionParamClauses.isEmpty ||
              (srr.functionParamClauses.head.parameters.isEmpty && canBeNullaryMethodApplication) ||
              isUnderscore ||
              fn.name == DynamicResolveProcessor.SELECT_DYNAMIC =>
            addExpectedTypeProblems()
          case fun: ScSyntheticFunction
            if fun.paramClauses == Seq() ||
              (fun.paramClauses == Seq(Seq()) && canBeNullaryMethodApplication) ||
              isUnderscore =>
            addExpectedTypeProblems()
          case method: PsiMethod
            if (method.parameters.isEmpty && canBeNullaryMethodApplication) ||
              isUnderscore =>
            addExpectedTypeProblems()
          case _ =>
            problems += MissedParametersClause(null)
            addExpectedTypeProblems()
        }
      }

      def methodTypeWithoutImplicits(tpe: ScType): ScType = tpe match {
        case ScMethodType(inner, _, true) => inner
        case t @ ScMethodType(inner, ps, false) =>
          ScMethodType(methodTypeWithoutImplicits(inner), ps)(t.elementScope)
        case ScTypePolymorphicType(internalType, tparams) =>
          ScTypePolymorphicType(methodTypeWithoutImplicits(internalType), tparams)
        case t => t
      }

      def checkEtaExpandedReference(fun: PsiNamedElement, pt: ScType): ApplicabilityCheckResult = {
        val maybeMethodType = fun match {
          case m: PsiMethod =>
            m.methodTypeProvider(place.elementScope)
              .polymorphicType(dropExtensionClauses = srr.shouldDropExtensionClauses)
              .toOption
          case fun: ScSyntheticFunction => fun.polymorphicType().toOption
          case _                        => None
        }

        val typeAfterConversions =
          maybeMethodType.map(methodTypeWithoutImplicits).flatMap { tpe =>
            val withUndefParams = tpe match {
              case ptpe: ScTypePolymorphicType =>
                val subst = ScSubstitutor.bind(ptpe.typeParameters)(UndefinedType(_))
                subst(ptpe.internalType.inferValueType)
              case tpe => tpe.inferValueType
            }

            val expr = Expression(withUndefParams, place)

            expr.getTypeAfterImplicitConversion(
              checkImplicits = true,
              isShape        = false,
              Option(pt)
            ).tr.toOption
          }

        val constraints =
          typeAfterConversions.map(tpe =>
            substitutor(tpe).isConservativelyCompatible(pt)
          ).getOrElse(ConstraintsResult.Left)

        constraints match {
          case ConstraintsResult.Left => ApplicabilityCheckResult(ExpectedTypeMismatch)
          case cs: ConstraintSystem   => ApplicabilityCheckResult(problems.result(), cs)
        }
      }

      fun match {
        case _: ScFunction if srr.functionParamClauses.isEmpty =>
          return addExpectedTypeProblems()
        case fun: ScSyntheticFunction if fun.paramClauses.isEmpty =>
          return addExpectedTypeProblems()
        case _ =>
      }

      val functionLikeType = FunctionLikeType(place)

      expectedOption().map {
        case abs: ScAbstractType => abs.simplifyType
        case t                   => t
      } match {
        case Some(pt @ functionLikeType(_, _, paramTpes)) =>
          val doNotEtaExpand = isPolymorphic && paramTpes.exists {
            case FullyAbstractType() => true
            case _                   => false
          }

          if (doNotEtaExpand) default()
          else                checkEtaExpandedReference(fun, pt)
        case _ => default()
      }
    }

    def checkSimpleApplication(): ApplicabilityCheckResult = {
      val typeArgCount         = currentTypeArgsClause.length
      val typeParamCount       = typeParametersForIdx.length
      val isAliasedConstructor = srr.parentElement.exists(_.is[ScTypeAliasDefinition])

      if (!isAliasedConstructor && typeArgCount > 0 && typeArgCount != typeParamCount) {
        if (typeParamCount == 0) problems += DoesNotTakeTypeParameters
        else if (typeParamCount < typeArgCount)
          problems ++= currentTypeArgsClause.drop(typeParamCount).map(ExcessTypeArgument)
        else
          problems ++= typeParametersForIdx
            .drop(typeArgCount)
            .map(MissedTypeParameter)

        addExpectedTypeProblems()
      } else {
        val expectedTypeProblems = addExpectedTypeProblems()

        val expectedTypeSubst =
          expectedTypeProblems.constraints.substitutionBounds(canThrowSCE = false)

        val substitutorWithExpected =
          expectedTypeSubst.fold(substitutor)(bounds => substitutor.followed(bounds.substitutor))

        val argsApplicability =
          Compatibility.compatible(
            srr,
            substitutorWithExpected,
            effectiveArgs,
            checkArgsWithImplicits,
            shapesOnly,
            place,
            clauseIdx
          )

        problems ++= argsApplicability.problems
        argsApplicability.copy(problems = problems.result())
      }
    }

    def correctTypeArgsSupplied(tparamsLength: Int): Boolean =
      currentTypeArgsClause.isEmpty ||
        currentTypeArgsClause.length == tparamsLength ||
        currentTypeArgsClause.forall(_.isNamed)

    val result = element match {
      //objects
      case obj: ScObject =>
        if (currentArgumentsClause.isEmpty) {
          expectedOption().map(_.removeAbstracts) match {
            case Some(FunctionType(_, _)) => problems += ExpectedTypeMismatch
            case Some(tp: ScType) if obj.isSAMEnabled =>
              SAMUtil.SAMToFunctionType(tp, obj) match {
                case Some(FunctionType(_, _)) => problems += ExpectedTypeMismatch
                case _                        => ()
              }
            case _ =>
          }
        } else {
          problems += DoesNotTakeParameters
        }
        ApplicabilityCheckResult(problems.result())
      case _: PsiClass    => ApplicabilityCheckResult(problems.result())
      case _: ScTypeAlias => ApplicabilityCheckResult(problems.result())
      case f: ScMethodLike if hasMalformedSignature(f) =>
        problems += MalformedDefinition(f.name)
        ApplicabilityCheckResult(problems.result())
      //application to implicit arguments only
      case _: ScFunction if
        correctTypeArgsSupplied(typeParamsSize) &&
          srr.functionParamClauses.forall(_.isImplicit) &&
          currentArgumentsClause.isEmpty =>
        addExpectedTypeProblems()
      //eta-expansion
      case (fun: ScTypeParametersOwner) & (_: PsiNamedElement)
        if correctTypeArgsSupplied(typeParamsSize) && currentArgumentsClause.isEmpty =>
        checkFunctionReference(fun, typeParamsSize != 0)
      case (fun: PsiTypeParameterListOwner) & (_: PsiNamedElement)
        if correctTypeArgsSupplied(typeParamsSize) && currentArgumentsClause.isEmpty =>
        checkFunctionReference(fun, typeParamsSize != 0)
      //simple application including empty application
      case _: ScTypeParametersOwner with PsiNamedElement     => checkSimpleApplication()
      case _: PsiTypeParameterListOwner with PsiNamedElement => checkSimpleApplication()
      case _ =>
        if (currentTypeArgsClause.nonEmpty) problems += DoesNotTakeTypeParameters
        if (currentArgumentsClause.nonEmpty) problems += DoesNotTakeParameters
        addExpectedTypeProblems()
    }

    val applicabilityCheckResult =
      if (result.problems.forall(_ == ExpectedTypeMismatch)) {
        val maybeResult = result.constraints match {
          case undefined @ ConstraintSystem(newSubstitutor) =>
            val typeParamIds = allTypeParameters.map(_.typeParamId).toSet

            var uSubst = undefined
            for (typeParam @ TypeParameter(tParam, _, lowerType, upperType) <- allTypeParameters) {
              val typeParamId = tParam.typeParamId

              if (currentTypeArgsClause.nonEmpty &&
                currentClauseTypeParamsSubst.isApplicableToTypeParam(typeParamId)) {
                val typeParamValue = currentClauseTypeParamsSubst(TypeParameterType(typeParam))
                uSubst = uSubst
                  .withLower(typeParamId, typeParamValue)
                  .withUpper(typeParamId, typeParamValue)
              }

              if (!lowerType.isNothing) {
                candidateSubstitutor(newSubstitutor(lowerType)) match {
                  case lower if !lower.hasRecursiveTypeParameters(typeParamIds) =>
                    uSubst = uSubst.withLower(typeParamId, lower)
                      .withTypeParamId(typeParamId)
                  case _ =>
                }
              }

              if (!upperType.isAny) {
                candidateSubstitutor(newSubstitutor(upperType)) match {
                  case upper if !upper.hasRecursiveTypeParameters(typeParamIds) =>
                    uSubst = uSubst.withUpper(typeParamId, upper)
                      .withTypeParamId(typeParamId)
                  case _ =>
                }
              }
            }

            uSubst match {
              case ConstraintSystem(_) => Some(result)
              case _                   => None
            }
          case _ => None
        }

        maybeResult.getOrElse {
          result.copy(problems = Seq(WrongTypeParameterInferred))
        }
      } else result


    val newConstraints = applicabilityCheckResult.constraints

    val typeArgsSubst =
      if (currentTypeArgsClause.nonEmpty && shapesOnly) // shapesOnly check helps avoid adding it twice unnecessarily
        currentClauseTypeParamsSubst
      else
        ScSubstitutor.empty

    val updatedSrr = srr.copy(
      problems                 = applicabilityCheckResult.problems,
      subst                    = srr.substitutor.followed(typeArgsSubst),
      applicabilityConstraints = srr.applicabilityConstraints + newConstraints
    )

    //avoid adding type parameters during the initial shape check
    val processedTypeParameters =
      if (shapesOnly) Seq.empty
      else            typeParametersForIdx

    val checkedCandidate = candidate.copy(
      resolveResult             = updatedSrr,
      processedTypeParams       = candidate.processedTypeParams ++ processedTypeParameters, //add type params corresponding to the current clause
      usesDefaultParameterValue = applicabilityCheckResult.defaultParameterUsed,
      usesSAMAdaptation         = applicabilityCheckResult.matched.exists(_.samAdapted)
    )

    checkedCandidate
  }

  private[lang] def typeParametersForArgClause(
    element:             PsiElement,
    argClauseIdx:        Int,
    isExtension:         Boolean,
    exportedInExtension: Option[ScExtension],
    argClauses:          Seq[Seq[Expression]] = Seq.empty
  ): Option[Seq[TypeParameter]] = {
    @tailrec
    def byArgClauseIndex(
      clauses:             Seq[ScSignatureClause],
      targetArgClauseIdx:  Int,
      currentArgClauseIdx: Int
    ): Option[Seq[TypeParameter]] =
      clauses match {
        case Seq() => None
        case ScSignatureClause.TypeClause(clause) +: _ if currentArgClauseIdx == targetArgClauseIdx =>
          Option(
            clause.typeParameters.map(TypeParameter(_))
          )
        case ScSignatureClause.TypeClause(_) +: tail =>
          byArgClauseIndex(tail, targetArgClauseIdx, currentArgClauseIdx)
        case ScSignatureClause.TermClause(clause) +: tail =>
          val explicitArgs = argClauses.lift(currentArgClauseIdx)

          val omittedUsingClause = clause.hasUsingKeyword &&
            !explicitArgs.exists(Compatibility.isExplicitUsingArgClause)

          if (omittedUsingClause)
            byArgClauseIndex(tail, targetArgClauseIdx, currentArgClauseIdx)
          else if (currentArgClauseIdx >= targetArgClauseIdx)
            None
          else
            byArgClauseIndex(tail, targetArgClauseIdx, currentArgClauseIdx + 1)
      }

    element match {
      case ScalaConstructor(cons) =>
        Option.when(argClauseIdx == 0)(
          cons.getConstructorTypeParameters.map(TypeParameter(_))
        )
      case cons @ Constructor.ofClass(cls) =>
        Option.when(argClauseIdx == 0)(
          (cls.getTypeParameters ++ cons.getTypeParameters).instantiate
        )
      case fun: ScFunction =>
        val extensionTypeParameters =
          if (!isExtension)
            exportedInExtension
              .orElse(fun.extensionMethodOwner)
              .toSeq
              .flatMap(_.typeParameters.map(TypeParameter(_)))
          else
            Seq.empty

        val clauseTypeParameters =
          if (extensionTypeParameters.nonEmpty) {
            if (argClauseIdx == 0) extensionTypeParameters
            else
              byArgClauseIndex(fun.signatureClauses, argClauseIdx - 1, 0).getOrElse(Seq.empty)
          } else {
            val allClauses = fun.signatureClauses

            byArgClauseIndex(allClauses, argClauseIdx, 0).getOrElse(Seq.empty)
          }

        Option(clauseTypeParameters)
      case owner: PsiTypeParameterListOwner =>
        Option.when(argClauseIdx == 0)(
          owner.getTypeParameters.instantiate
        )
      case syn: ScSyntheticFunction => Option.when(argClauseIdx == 0)(syn.typeParameters.map(TypeParameter(_)))
      case _ => None
    }
  }

  private def undefinedOrTypeArgsSubstitutor(
    subst:                  ScSubstitutor,
    selfConstructorResolve: Boolean,
    typeParameters:         Seq[TypeParameter],
    typeArgs:               Seq[ScTypeArgument],
  ): ScSubstitutor = {
    if (selfConstructorResolve) return ScSubstitutor.empty

    val hasNamedTypeArgs = typeArgs.exists(_.isNamed)

    val follower =
      if (typeArgs.nonEmpty && typeParameters.length == typeArgs.length)
        ScSubstitutor.bind(typeParameters, typeArgs)
      else if (hasNamedTypeArgs) {
        //case where only some of the type arguments are provided
        val names             = typeArgs.flatMap(_.name)
        val targsSubst        = ScSubstitutor.bind(typeParameters, typeArgs)
        val missingTypeParams = typeParameters.filterNot(tp => names.contains(tp.name))
        val undefinedSubst    = ScSubstitutor.bind(missingTypeParams)(UndefinedType(_))
        targsSubst.followed(undefinedSubst)
      } else
        ScSubstitutor.bind(typeParameters)(UndefinedType(_))

    subst.followed(follower)
  }

  private def candidates(
    proc:  MethodResolveProcessor,
    input: Set[ScalaResolveResult],
  ): Set[ScalaResolveResult] = {
    import proc.{candidates => _, _}
    val argumentClauses = invocationClauses.collect { case InvocationClause(_, Some(args)) => args }
    val maxArgClauseIdx = invocationClauses.size - 1

    // Each candidate carries an arg clause shift: 0 for direct candidates,
    // expansionClauseIdx for apply-expanded ones. At global clauseIdx, a candidate
    // with shift s is checked at effective index (clauseIdx - s) using argumentClauses.drop(s).
    def candidatesForArgClause(
      prevResults:      Set[ApplicabilityCandidate],
      clauseIdx:        Int,
      withExpectedType: Boolean
    ): Set[ApplicabilityCandidate] = {

      // Step 1: Expand candidates in a single pass.
      //   clauseIdx == 0: expandApplyOrUpdateMethod (handles apply/update expansion)
      //   clauseIdx > 0, exhausted at effective index, Scala 3: expandApplyForReturnType
      //   otherwise: pass through
      val allExpanded: Set[ApplicabilityCandidate] =
        if (clauseIdx == 0)
          prevResults.flatMap(
            expandApplyOrUpdateMethod(_, proc)
          )
        else
          prevResults.flatMap { cand =>
            val r     = cand.resolveResult
            val shift = cand.shift
            //Try expanding apply methods if either
            //1. The candidate has no more parameter clauses and arg clause is being processed at the current iteration
            //2. The candidate has no more type parameter clauses and type arg clause is being processed at the current iteration
            val exhaustedParameterClauses = Compatibility.correspondingParamClause(
              r.functionParamClauses,
              argumentClauses.drop(shift),
              clauseIdx - shift
            ).isEmpty // 1.

            val exhaustedTypeParameterClauses = {
              val typeParamsForArgClause =
                typeParametersForArgClause(
                  r.element,
                  clauseIdx - shift,
                  r.isExtensionCall,
                  r.exportedInExtension,
                  argumentClauses.drop(shift)
                ).getOrElse(Seq.empty)

              typeParamsForArgClause.isEmpty && typeArgsForArgClause(clauseIdx - shift).nonEmpty // 2.
            }

            if (useScala3OverloadingRules && (exhaustedTypeParameterClauses || exhaustedParameterClauses))
              expandApplyForReturnType(
                cand,
                proc,
                clauseIdx,
                exhaustedTypeParameterClauses
              ).map(
                _.copy(shift = clauseIdx)
              )
            else Set(cand)
          }

      // Step 2: Shape check all candidates (grouped by shift for correct arg indexing)
      val shapeChecked =
        checkResultsApplicability(
          proc,
          allExpanded,
          checkWithImplicits    = false,
          useExpectedType       = withExpectedType,
          args                  = argumentClauses,
          argClauseIdx          = clauseIdx,
          shapesOnly            = true,
        )

      val applicableToShape = shapeChecked.filter(_.isApplicable(withExpectedType = withExpectedType))

      // Step 3: Full applicability check (grouped by shift)
      val checkedResults =
        if (isShapeResolve) {
          if (applicableToShape.nonEmpty) applicableToShape
          else                            shapeChecked
        } else {
          val preselected =
            if (applicableToShape.isEmpty) allExpanded
            else                           applicableToShape

          candidates(
            proc,
            preselected,
            argumentClauses,
            clauseIdx,
            useExpectedType = withExpectedType
          )
        }

      val applicable   = checkedResults.filter(_.isApplicable(withExpectedType = withExpectedType))
      val isLastClause = clauseIdx == Math.max(maxArgClauseIdx, 0) || !useScala3OverloadingRules

      if (applicable.isEmpty) {
        //Overloading resolution is tried twice, first with expected type (for the whole call), then
        //if no applicable alternatives are found without the expected type.
        //Only tried when processing the last (in scala 2 the only) argument clause, since
        //there is no point in checking expected type conformance on every iteration.
        if (withExpectedType && isLastClause) {
          val withoutExpected           = candidatesForArgClause(prevResults, clauseIdx, withExpectedType = false)
          val applicableWithoutExpected = withoutExpected.filter(_.isApplicable())

          /**
           * If we can't get an applicable resolve result even w/o an expected type,
           * return mapped (with expected type), because it's more intuitive, when displaying errors to user.
           */
          if (applicableWithoutExpected.isEmpty) checkedResults
          else                                   applicableWithoutExpected
        } else checkedResults
      } else if (useScala3OverloadingRules && applicable.size > 1) {
        if (clauseIdx < maxArgClauseIdx) {
          applicable.foreach(_.resetTypeArgsFlag())
          candidatesForArgClause(applicable, clauseIdx + 1, withExpectedType)
        } else {
          //prefer candidates that do not need eta-expansion
          val noEtaExpansion =
            applicable.filterNot(needsEtaExpansion(_, argumentClauses, clauseIdx))

          if (noEtaExpansion.nonEmpty) noEtaExpansion
          else                         applicable
        }
      } else applicable
    }

    val applicabilityCandidates = input.map(ApplicabilityCandidate(_))
    val results                 = candidatesForArgClause(applicabilityCandidates, 0, withExpectedType = true)

    results.map(_.resolveResult)
  }

  //Checks if there's (at least) one more non-implicit argument clause ahead.
  private def needsEtaExpansion(
    cand:            ApplicabilityCandidate,
    argumentClauses: Seq[Seq[Expression]],
    clauseIdx:       Int
  ): Boolean =
    Compatibility.correspondingParamClause(
      cand.resolveResult.functionParamClauses,
      argumentClauses.drop(cand.shift) ++ Seq(Seq.empty),
      clauseIdx + 1 - cand.shift
    ).nonEmpty

  private def candidates(
    proc:            MethodResolveProcessor,
    preselected:     Set[ApplicabilityCandidate],
    argumentClauses: Seq[Seq[Expression]],
    clauseIdx:       Int,
    useExpectedType: Boolean
  ): Set[ApplicabilityCandidate] = {
    import proc.{candidates => _, _}

    def applicableResults(cands: Set[ApplicabilityCandidate]): Set[ApplicabilityCandidate] =
      cands.collect { case cand if cand.isApplicable(withExpectedType = useExpectedType) => cand }

    var mapped = checkResultsApplicability(
      proc,
      preselected,
      checkWithImplicits = false,
      useExpectedType    = useExpectedType,
      args               = argumentClauses,
      argClauseIdx       = clauseIdx
    )

    var filtered = applicableResults(mapped)

    if (filtered.isEmpty && !noImplicitsForArgs) {
      /**
       * Allow implicit conversions, when typing argument expressions.
       */
      mapped = checkResultsApplicability(
        proc,
        preselected,
        checkWithImplicits = true,
        useExpectedType    = useExpectedType,
        args               = argumentClauses,
        argClauseIdx       = clauseIdx
      )

      filtered = applicableResults(mapped)
    }

    /**
     * Remove default parameters alternatives (in Scala 2 only)
     */
    if (filtered.size > 1 && !isShapeResolve && !useScala3OverloadingRules)
      filtered = filtered.filterNot(_.usesDefaultParameterValue)

    /**
     * SCL-24823: Prefer alternatives applicable without SAM adaptation.
     * If some overloads are directly applicable and others only via SAM,
     * the directly applicable ones win.
     */
    if (filtered.size > 1 && !isShapeResolve) {
      val nonSAM = filtered.filterNot(_.usesSAMAdaptation)
      if (nonSAM.nonEmpty) filtered = nonSAM
    }

    if (
      filtered.isEmpty &&
        !useExpectedType &&
        enableTupling &&
        argumentClauses.nonEmpty
    ) {
      /**
       * If everything else failed, try auto-tupling
       */
      val argsTupled = ScalaPsiUtil.tupled(argumentClauses.head, ref)

      if (argsTupled.nonEmpty) {
        val candsWithTupledArgs =
          checkResultsApplicability(
            proc,
            preselected,
            checkWithImplicits = true,
            useExpectedType    = false,
            args               = argsTupled.toList,
            argClauseIdx       = clauseIdx
          ).map(cand =>
            cand.copy(
              resolveResult = cand.resolveResult.copy(tuplingUsed = true), //@TODO: remove
              usesAutoTupling = true
            )
          )

        filtered = candsWithTupledArgs.filter(_.isApplicable())
      }
    }

    if (filtered.isEmpty) mapped
    else {
      val valueArgClauseAtIdx = argumentClauses.lift(clauseIdx) //beware, this can be empty, e.g. def foo(a: Int)[B]: Int = 123, clauseIdx := 1
      val len                 = valueArgClauseAtIdx.fold(0)(_.length)

      if (filtered.size == 1)     filtered
      else {

        // If there are still multiple results, try to select...
        // - the most specific normal methods first
        // - then the most specific extension methods
        // - and lastly, the most specific methods from implicit conversions
        // If none of these yields an unambiguous result, return all results
        val normalMethods    = Set.newBuilder[ApplicabilityCandidate]
        val extensionMethods = Set.newBuilder[ApplicabilityCandidate]
        val implicitMethods  = Set.newBuilder[ApplicabilityCandidate]

        for (cand <- filtered) {
          val rr = cand.resolveResult
          // Extensions from givens have the same precedence as methods from implicit conversions
          if (rr.implicitConversion.isDefined || rr.isExtensionFromGiven) implicitMethods  += cand
          else if (rr.isExtensionCall)                                    extensionMethods += cand
          else                                                            normalMethods    += cand
        }

        val mostSpecificUtil = MostSpecificUtil(ref, len)

        def selectMostSpecificOr(
          candidates:              Set[ApplicabilityCandidate],
          orElse:                  =>Set[ApplicabilityCandidate],
          isForImplicitResolution: Boolean
        ): Set[ApplicabilityCandidate] =
          if (candidates.sizeIs == 1) candidates
          else {
            val candidatesWithRespectiveParamClause =
              candidates.map { cand =>
                val srr = cand.resolveResult

                val paramClause =
                  Compatibility.correspondingParamClause(
                    srr.functionParamClauses,
                    argumentClauses.drop(cand.shift),
                    cand.effectiveClauseIdx(clauseIdx)
                  )

                (srr, paramClause)
              }

            mostSpecificUtil.mostSpecificForParameterClause(
              candidatesWithRespectiveParamClause,
              isForImplicitResolution
            ) match {
              case Some(rr) =>
                val correspondingCandidate = candidates.find(_.resolveResult == rr)
                correspondingCandidate.toSet
              case None => orElse
            }
          }

        selectMostSpecificOr(
          normalMethods.result(),
          selectMostSpecificOr(
            extensionMethods.result(),
            selectMostSpecificOr(
              implicitMethods.result(),
              filtered,
              isForImplicitResolution = true
            ),
            isForImplicitResolution = true
          ),
          isForImplicitResolution = false
        )
      }
    }
  }

  private def callContextForClauseIdx(
    ref:                       PsiElement,
    clauseIdx:                 Int,
    exhaustedTypeParamClauses: Boolean
  ): PsiElement = {

    @tailrec
    def traverse(ctx: PsiElement, remaining: Int): PsiElement = {
      if (remaining <= 0) {
        ctx match {
          case gen: ScGenericCall if exhaustedTypeParamClauses => gen.referencedExpr
          case other                                           => other
        }
      } else ctx.getContext match {
        case null =>
          ctx
        case (_: ScGenericCall) contextChildOf (inv: MethodInvocation) => traverse(inv, remaining - 1)
        case gen: ScGenericCall                                        => traverse(gen, remaining - 1)
        case inv: MethodInvocation                                     => traverse(inv, remaining - 1)
        case other                                                     => traverse(other.getContext, remaining)
      }
    }

    traverse(ref, clauseIdx)
  }

  private def expandApplyForReturnType(
    cand:                      ApplicabilityCandidate,
    proc:                      MethodResolveProcessor,
    clauseIdx:                 Int,
    exhaustedTypeParamClauses: Boolean
  ): Set[ApplicabilityCandidate] = {
    import proc._
    val initialRR = cand.resolveResult

    val constraintsSubst = initialRR.applicabilityConstraints.substOrEmpty
    val subst            = initialRR.substitutor.followed(constraintsSubst)

    val returnType = initialRR.element match {
      case m: PsiMethod => Option(m.getReturnType).map(_.toScType()).map(subst)
      case _            => None
    }

    returnType.map { tp =>
      val callCtx = callContextForClauseIdx(ref, clauseIdx, exhaustedTypeParamClauses)

      val applyCandidates =
        callCtx.resolveApplyOrUpdateMethod(
          callCtx,
          tp,
          shapesOnly    = isShapeResolve,
          withImplicits = false
        )

      applyCandidates.collect {
        case applyRR if !accessibility || isAccessible(applyRR.element, ref) =>
          val topLevelApply = applyRR.mostInnerResolveResult

          cand.copy(
            resolveResult = topLevelApply.copy(
              innerResolveResult       = Option(initialRR),
              parentElement            = initialRR.element.toOption,
              importsUsed              = initialRR.importsUsed,
              unresolvedTypeParameters = initialRR.unresolvedTypeParameters
            ),
            stripTypeArgs = !exhaustedTypeParamClauses
          )
      }.toSet
    }.getOrElse(Set.empty)
  }

  /**
   * This method is only called, when processing candidate expansion at the first parameter clause.
   * See also: [[expandApplyForReturnType()]].
   */
  private def expandApplyOrUpdateMethod(
    cand: ApplicabilityCandidate,
    proc: MethodResolveProcessor
  ): Set[ApplicabilityCandidate] = {
    import proc._
    val noExpansion        = Set(cand)
    val r                  = cand.resolveResult
    val typeArgs           = typeArgsForArgClause(0)
    val args               = valueArgsForArgClause(0)
    val typeParams         = typeParametersForArgClause(r.element, 0, r.isExtensionCall, r.exportedInExtension, args.toSeq).getOrElse(Seq.empty)
    val typeArgsSubst      = ScSubstitutor.bind(typeParams, typeArgs)
    val subst              = r.substitutor.followed(typeArgsSubst)
    val mismatchedTypeArgs = typeParams.isEmpty && typeArgs.nonEmpty

    def applyOrUpdateMethodsFor(tp: ScType): Set[ApplicabilityCandidate] = {
      val cleanTypeArguments = typeParams.nonEmpty

      val curriedTypeParams  =
        if (typeParams.nonEmpty && typeArgs.isEmpty) typeParams
        else                                         Seq.empty

      val callContext = ref.getContext match {
        case _: MethodInvocation => ref.toOption
        case gen: ScGenericCall =>
          if (typeParams.nonEmpty) gen.toOption // foo[A](123) = foo[A].apply(123) case
          else                     ref.toOption //             = foo.apply[A](123) case
        case paren: ScParenthesisedExpr => paren.toOption
        case _                          => None
      }

      val applyCandidates = callContext.toArray.flatMap(e =>
        e.resolveApplyOrUpdateMethod(
          e,
          subst(tp),
          shapesOnly    = isShapeResolve,
          withImplicits = false
        )
      )

      if (applyCandidates.isEmpty)
        noExpansion
      else
        applyCandidates.view.collect {
          case rr if !accessibility || isAccessible(rr.element, ref) =>
            val unresolvedTypeParameters =
              if (curriedTypeParams.nonEmpty)
                Option(rr.unresolvedTypeParameters.fold(curriedTypeParams)(_ ++ curriedTypeParams))
              else
                rr.unresolvedTypeParameters

            cand.copy(
              resolveResult = rr.copy(
                innerResolveResult       = Option(r),
                parentElement            = r.element.toOption,
                importsUsed              = r.importsUsed,
                unresolvedTypeParameters = unresolvedTypeParameters
              ),
              stripTypeArgs = cleanTypeArguments
            )
        }.toSet
    }

    if (args.isEmpty && typeArgs.isEmpty || r.name == CommonNames.Apply)
      noExpansion
    else {
      val hasParams = r.elementHasParameters || r.element.asOptionOf[ScFunction].exists(_.hasEmptyParenSuperMethod)

      r.element match {
        case synthetic: ScSyntheticFunction =>
          if (!hasParams && (args.nonEmpty || mismatchedTypeArgs))
            applyOrUpdateMethodsFor(synthetic.polymorphicType())
          else
            noExpansion
        case f: PsiMethod =>
          if (!hasParams && (args.nonEmpty || mismatchedTypeArgs))
            applyOrUpdateMethodsFor(
              f.methodTypeProvider(proc.ref.elementScope)
               .polymorphicType(dropExtensionClauses = r.isExtensionCall)
            )
          else
            noExpansion
        case b: ScTypedDefinition =>
          val tpe =
            if (b.isStable)
              r.fromType match {
                case Some(tp) => ScProjectionType(tp, b).toOption
                case None     => ScDesignatorType(b).toOption
              }
            else b.`type`().toOption

          tpe.map(applyOrUpdateMethodsFor).getOrElse(noExpansion)
        case b: PsiField => // See SCL-3055
          applyOrUpdateMethodsFor(b.getType.toScType())
        case _ => noExpansion
      }
    }
  }

  private def checkResultsApplicability(
    proc:                  MethodResolveProcessor,
    expandedInput:         Set[ApplicabilityCandidate],
    checkWithImplicits:    Boolean,
    useExpectedType:       Boolean,
    args:                  Seq[Seq[Expression]],
    argClauseIdx:          Int,
    shapesOnly:            Boolean = false,
  ): Set[ApplicabilityCandidate] = {
    import proc._

    val resultBuilder = Set.newBuilder[ApplicabilityCandidate]
    val iterator      = expandedInput.iterator

    while (iterator.hasNext) {
      val applicabilityCandidate = iterator.next()
      val currentClauseTypeArgs  = typeArgsForArgClause(argClauseIdx)
      val currentClauseArgs      = valueArgsForArgClause(argClauseIdx)

      val actualTypeArgs =
        if (applicabilityCandidate.stripTypeArgs) Seq.empty
        else                                      currentClauseTypeArgs

      val isLastClause =
        !useScala3OverloadingRules || // we only check the first clause in scala 2
          argClauseIdx == Math.max(invocationClauses.size - 1, 0)

      val checkedCandidate = updateCandidateWithApplicabilityProblems(
        getPlace,
        applicabilityCandidate,
        checkWithImplicits,
        args,
        actualTypeArgs,
        currentClauseArgs,
        prevTypeInfo,
        if (useExpectedType) expectedOption else () => None,
        selfConstructorResolve = selfConstructorResolve,
        isUnderscore           = isUnderscore,
        shapesOnly             = shapesOnly,
        argClauseIdx           = argClauseIdx,
        isLastClause           = isLastClause
      )

      resultBuilder += checkedCandidate
    }
    resultBuilder.result()
  }

  /**
   * @return True, if `method` has repeated parameters
   */
  private def hasMalformedSignature(method: ScMethodLike) =
    method.parameterList.clauses.exists {
      _.parameters.dropRight(1).exists(_.isRepeatedParameter)
    }

  case class InvocationClause(
    targs: Option[Seq[ScTypeArgument]] = None,
    args:  Option[Seq[Expression]]     = None
  ) {
    assert(targs.isDefined || args.isDefined, "Either type args or value args must be defined")
  }

  object InvocationClause {
    def argsOnly(args: Seq[Expression]): InvocationClause = InvocationClause(args = args.toOption)
  }

  case class InvocationInfo(
    invocationClauses: Seq[InvocationClause],
    expectedType:      () => Option[ScType],
    isUnderscore:      Boolean,
    invokedExpr:       Option[ScExpression]
  )

  private def typeArgClause(typeArgs: Option[Seq[ScTypeArgument]]): Option[InvocationClause] =
    if (typeArgs.nonEmpty) InvocationClause(targs = typeArgs).toOption
    else                   None

  @tailrec
  def getInvocationInfo(
    ref:               PsiElement,
    e:                 PsiElement,
    invocationClauses: Seq[InvocationClause]       = Seq.empty,
    typeArgs:          Option[Seq[ScTypeArgument]] = None,
  ): InvocationInfo =
    e.getContext match {
      case generic: ScGenericCall if generic.referencedExpr == e =>
        val newTypeArgs = Option.when(generic.typeArguments.nonEmpty)(generic.typeArguments)

        if (typeArgs.isDefined) {
          //foo[A][B] case, since we cannot have back to back type parameter clauses,
          //the only option here is an expanded apply call foo[A].apply[B],
          //this is arguably a separate invocation altogether, so just short circuit here.
          //Otherwise, we end up with weird scenarios, e.g. typeOf(foo[A]) == typeOf(foo[A][B])
          //since they would resolve to the same apply method.
          val clause = typeArgClause(typeArgs)

          InvocationInfo(
            invocationClauses ++ clause,
            () => generic.expectedType(),
            isUnderscore = false,
            generic.referencedExpr.toOption
          )
        } else
          getInvocationInfo(
            ref,
            generic,
            invocationClauses,
            typeArgs = newTypeArgs,
          )
      case call: ScMethodCall if !call.isUpdateCall && call.getInvokedExpr == e =>
        val clause =
          InvocationClause(
            args  = call.argumentExpressions.toOption,
            targs = typeArgs
          )

        getInvocationInfo(
          ref,
          call,
          invocationClauses :+ clause,
          typeArgs = None,
        )
      case call: ScMethodCall if call.getInvokedExpr == e =>
        val args = call.argumentExpressions ++
          call.getContext.asInstanceOf[ScAssignment].rightExpression.toList

        val clause =
          InvocationClause(
            args = args.toOption,
            targs = typeArgs
          )

        getInvocationInfo(
          ref,
          call,
          invocationClauses :+ clause,
          typeArgs = None,
        )
      case section: ScUnderscoreSection =>
        val finalClause = typeArgClause(typeArgs)

        InvocationInfo(
          invocationClauses ++ finalClause,
          () => section.expectedType(),
          isUnderscore = true,
          None
        )
      case infix @ ScInfixExpr.withAssoc(baseExpr, `ref`, argument) =>
        val args =
          argument match {
            case tuple: ScTuple         => Option(tuple.exprs) // See SCL-2001
            case _: ScUnitExpr          => Option(Seq.empty) // See SCL-3485
            case e: ScParenthesisedExpr =>
              e.innerElement match {
                case Some(expr)           => Option(Seq(expr))
                case _                    => None
              }
            case rOp => Option(Seq(rOp))
          }

        //        val postFixRef =
        //          ScalaPsiElementFactory.createExpressionWithContextFromText(s"${baseExpr.getText} ${ref.getText}", infix)

        val clause =
          InvocationClause(
            args = args,
            targs = typeArgs
          )

        getInvocationInfo(
          ref,
          infix,
          invocationClauses :+ clause,
          typeArgs = None,
        )
      //        ContextInfo(
      //          invocationClauses,
      //          () => infix.expectedType(),
      //          isUnderscore = false,
      //          Option(postFixRef)
      //        )
      case parents: ScParenthesisedExpr                   => getInvocationInfo(ref, parents, invocationClauses, typeArgs)
      case postf: ScPostfixExpr if ref == postf.operation => getInvocationInfo(ref, postf, invocationClauses, typeArgs)
      case pref: ScPrefixExpr if ref == pref.operation    => getInvocationInfo(ref, pref, invocationClauses, typeArgs)
      case _ =>
        val finalClause = typeArgClause(typeArgs)

        val expectedType = () => e match {
          case expr: ScExpression => expr.expectedType()
          case _                  => None
        }

        InvocationInfo(
          invocationClauses ++ finalClause,
          expectedType,
          isUnderscore = false,
          None
        )
    }


  /**
   * Intermediate representation of a [[ScalaResolveResult]] going through a
   * clause-by-clause applicability checks.
   *
   * @param processedTypeParams       All type parameters processed up to this point.
   *                                  e.g.
   *                                  {{{
   *                                    def foo[A](a: A)[B](b: B)[C](c: C): Int = 1
   *                                  }}}
   *                                  At [[currentClauseIdx]] == 2, processing `[C](c: C)`,
   *                                  [[processedTypeParams]] would be `Seq(A, B)`.
   * @param shift                     If [[resolveResult]] is an apply/update method expansion — the invocation clause index
   *                                  at which it was expanded, e.g.
   *                                  {{{
   *                                    class Foo { def apply(x: Int) = 1 }
   *                                    def foo(x: Int): Foo = 2
   *                                    foo(1)(2)
   *                                  }}}
   *                                  At [[currentClauseIdx]] == 1 [[resolveResult]] would be an `apply` method on `Foo` and [[shift]]
   *                                  would be 1. Thus, effective invocation clause index is always [[currentClauseIdx]] - [[shift]].
   * @param stripTypeArgs             True if [[resolveResult]] is an apply/update method expansion and type arguments of the current invocation
   *                                  clause do NOT belong to it (but rather to the method whose result type was expanded), False otherwise.
   *                                  e.g.
   *                                  {{{
   *                                    class Foo[A] { def apply(x: A): Int = 1 }
   *                                    def foo[A]: Foo[A] = ???
   *                                    foo[Int](1)
   *                                  }}}
   *                                  Here `[Int]` type argument clause belongs to the `foo` method, not to `apply` [[resolveResult]].
   * @param usesSAMAdaptation         Whether this candidate was only applicable through SAM
   *                                  (Single Abstract Method) type adaptation. Used in overload resolution
   *                                  to prefer alternatives that are directly applicable over SAM-adapted ones.
   * @param usesDefaultParameterValue Whether applicability required default parameter values
   *                                  to fill missing arguments. Used in overload resolution
   *                                  (Scala 2 prefers alternatives without defaults).
   * @param usesAutoTupling           Whether auto-tupling was needed to make the call applicable.
   *                                  {{{
   *                                  def f(t: (Int, Int)): Unit = ...
   *                                  f(1, 2)  // auto-tupled to f((1, 2));
   *                                  }}}
   */
  private case class ApplicabilityCandidate(
    resolveResult:             ScalaResolveResult,
    processedTypeParams:       Seq[TypeParameter]       = Seq.empty,
    shift:                     Int                      = 0,
    var stripTypeArgs:         Boolean                  = false,
    usesSAMAdaptation:         Boolean                  = false,
    usesDefaultParameterValue: Boolean                  = false,
    usesAutoTupling:           Boolean                  = false
  ) {
    def effectiveClauseIdx(globalClauseIdx: Int): Int = globalClauseIdx - shift

    def isApplicable(withExpectedType: Boolean = false): Boolean =
      resolveResult.isApplicable(withExpectedType)

    def resetTypeArgsFlag(): Unit = stripTypeArgs = false
  }
}
