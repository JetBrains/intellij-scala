package org.jetbrains.plugins.scala.lang.resolve.processor

import com.intellij.psi._
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiUtil
import org.jetbrains.plugins.scala.lang.psi.api.base._
import org.jetbrains.plugins.scala.lang.psi.api.base.types.ScTypeElement
import org.jetbrains.plugins.scala.lang.psi.api.expr._
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScFunction.CommonNames
import org.jetbrains.plugins.scala.lang.psi.api.statements._
import org.jetbrains.plugins.scala.lang.psi.api.statements.params.{ScParameterClause, TypeParamIdOwner}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.{ScMember, ScObject, ScTypeDefinition}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.{ScTypeParametersOwner, ScTypedDefinition}
import org.jetbrains.plugins.scala.lang.psi.impl.ScPackageImpl
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementFactory.createExpressionFromText
import org.jetbrains.plugins.scala.lang.psi.impl.toplevel.synthetic.ScSyntheticFunction
import org.jetbrains.plugins.scala.lang.psi.types.Compatibility.{ApplicabilityCheckResult, Expression}
import org.jetbrains.plugins.scala.lang.psi.types._
import org.jetbrains.plugins.scala.lang.psi.types.api._
import org.jetbrains.plugins.scala.lang.psi.types.api.designator.{ScDesignatorType, ScProjectionType}
import org.jetbrains.plugins.scala.lang.psi.types.nonvalue.{ScMethodType, ScTypePolymorphicType}
import org.jetbrains.plugins.scala.lang.psi.types.recursiveUpdate.ScSubstitutor
import org.jetbrains.plugins.scala.lang.psi.types.result._
import org.jetbrains.plugins.scala.lang.resolve.MethodTypeProvider._
import org.jetbrains.plugins.scala.lang.resolve.ResolveUtils.ScExpressionForExpectedTypesEx
import org.jetbrains.plugins.scala.lang.resolve.ScalaResolveState.ResolveStateExt
import org.jetbrains.plugins.scala.lang.resolve.{ResolveTargets, ScalaResolveResult, StdKinds}
import org.jetbrains.plugins.scala.project.{ProjectContext, ProjectPsiElementExt}
import org.jetbrains.plugins.scala.util.SAMUtil

import scala.annotation.tailrec

class MethodResolveProcessor(
  override val ref:           PsiElement,
  val refName:                String,
  val argumentClauses:        Seq[Seq[Expression]],
  val typeArgElements:        Seq[ScTypeElement],
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
  def copy(
    ref:                    PsiElement                = ref,
    refName:                String                    = refName,
    argumentClauses:        Seq[Seq[Expression]]      = argumentClauses,
    typeArgElements:        Seq[ScTypeElement]        = typeArgElements,
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
    argumentClauses,
    typeArgElements,
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
    def implType: Option[ScType]                             = state.implicitType
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
          implicitConversion       = implFunction,
          implicitType             = implType,
          fromType                 = fromType,
          isNamedParameter         = isNamedParameter,
          isAccessible             = accessible,
          isForwardReference       = forwardReference,
          unresolvedTypeParameters = unresolvedTypeParameters,
          isExtensionCall          = extensionMethod,
          extensionContext         = extensionContext,
          matchClauseSubstitutor   = state.matchClauseSubstitutor,
          intersectedReturnType    = intersectedReturnType,
          exportedInfo             = exportedInfo
        )

      namedElement match {
        case m: PsiMethod => addResult(resultBuilder(m))
        case o: ScObject if o.isPackageObject =>  // do not resolve to package object
        case obj: ScObject if ref.getParent.is[ScMethodCall] || ref.getParent.is[ScGenericCall] =>
          addResult(resultBuilder(obj))
        case cls: PsiClass
          if ref.isInScala3Module &&
            (ref.getParent.is[ScMethodCall] || ref.getParent.is[ScGenericCall]) =>
          // process constructor proxies
          val constructors = cls.constructors

          val withAccessibilityCheck =
            constructors.view.map { cons =>
              new ScalaResolveResult(
                cons,
                ScSubstitutor.empty,
                importsUsed,
                renamed,
                implicitConversion = implFunction,
                implicitType       = implType,
                fromType           = fromType,
                parentElement      = Option(cls),
                isAccessible       = isAccessible(cons, ref),
                isForwardReference = forwardReference
              )
            }.filter(srr => !accessibility || srr.isAccessible)

          addResults(withAccessibilityCheck)
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
  private def problemsFor(
    place:                  PsiElement,
    c:                      ScalaResolveResult,
    checkWithImplicits:     Boolean,
    ref:                    PsiElement,
    argumentClauses:        Seq[Seq[Expression]],
    typeArgElements:        Seq[ScTypeElement],
    prevTypeInfo:           Seq[TypeParameter],
    expectedOption:         () => Option[ScType],
    selfConstructorResolve: Boolean,
    isUnderscore:           Boolean,
    shapesOnly:             Boolean,
    argClauseIdx:           Int
  ): ApplicabilityCheckResult = {
    def paramClauses(fun: ScFunction): Seq[ScParameterClause] =
      if (c.shouldDropExtensionClauses) fun.paramClauses.clauses
      else                              fun.parameterClausesWithExtension(c.exportedInExtension)

    //If current candidate is a *Dynamic method, first argument clause is always the invoked name.
    val actualArgClauseIdx = argClauseIdx - (if (c.nameArgForDynamic.isDefined) 1 else 0)

    implicit val projectContext: ProjectContext = c.element
    implicit val context: Context = Context(place)

    val problems             = Seq.newBuilder[ApplicabilityProblem]
    val element              = c.element
    val candidateSubstitutor = c.substitutor

    val elementsForUndefining = element match {
      case ScalaConstructor(_) if !selfConstructorResolve => Seq(c.getActualElement)
      case Constructor(_)                                 => Seq(c.getActualElement, element).distinct
      case _                                              => Seq(element)
    }

    val iterator        = elementsForUndefining.iterator
    var tempSubstitutor = ScSubstitutor.empty

    while (iterator.hasNext) {
      val element = iterator.next()

      tempSubstitutor = tempSubstitutor.followed(
        undefinedOrTypeArgsSubstitutor(
          element,
          candidateSubstitutor,
          selfConstructorResolve,
          typeArgElements,
          c.isExtensionCall,
          c.exportedInExtension
        )
      )
    }

    val unresolvedTps = c.unresolvedTypeParameters.getOrElse(Seq.empty)

    val substitutor =
      tempSubstitutor.followed(ScSubstitutor.bind(prevTypeInfo ++ unresolvedTps)(UndefinedType(_)))

    val typeParameters: Seq[TypeParameter] = prevTypeInfo ++ (element match {
      case ScalaConstructor(cons) => cons.getConstructorTypeParameters.map(TypeParameter(_))
      case cons @ Constructor.ofClass(cls) =>
        (cls.getTypeParameters ++ cons.getTypeParameters).toSeq.map(TypeParameter(_))
      case fun: ScFunction => fun.typeParameters.map(TypeParameter(_))
      case fun: PsiMethod  => fun.getTypeParameters.map(TypeParameter(_)).toSeq
      case _               => Seq.empty
    })

    def addExpectedTypeProblems(): ApplicabilityCheckResult = {
      if (expectedOption().isEmpty) {
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
        case f: ScFunction if paramClauses(f).count(!_.isImplicit) > 1 =>
          problems += ExpectedTypeMismatch //do not check expected types for more than one param clauses
          Nothing
        case f: ScFunction => substitutor(f.returnType.getOrNothing)
        case f: ScFun      => substitutor(f.retType)
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

    def checkFunctionReference(fun: PsiNamedElement, isPolymorphic: Boolean): ApplicabilityCheckResult = {
      def default(): ApplicabilityCheckResult = {
        fun match {
          case fun: ScFunction if paramClauses(fun).isEmpty ||
            paramClauses(fun).head.parameters.isEmpty ||
            isUnderscore => ApplicabilityCheckResult(problems.result())
          case fun: ScFun if fun.paramClauses == Seq() || fun.paramClauses == Seq(Seq()) || isUnderscore =>
            addExpectedTypeProblems()
          case method: PsiMethod if method.parameters.isEmpty ||
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
          ScMethodType(methodTypeWithoutImplicits(inner), ps, isImplicit = false)(t.elementScope)
        case ScTypePolymorphicType(internalType, tparams) =>
          ScTypePolymorphicType(methodTypeWithoutImplicits(internalType), tparams)
        case t => t
      }

      def checkEtaExpandedReference(fun: PsiNamedElement, pt: ScType): ApplicabilityCheckResult = {
        val maybeMethodType = fun match {
          case m: PsiMethod =>
            m.methodTypeProvider(ref.elementScope)
              .polymorphicType(dropExtensionClauses = c.shouldDropExtensionClauses)
              .toOption
          case fun: ScFun   => fun.polymorphicType().toOption
          case _            => None
        }

        val typeAfterConversions =
          maybeMethodType.map(methodTypeWithoutImplicits).flatMap { tpe =>
            val withUndefParams = tpe match {
              case ptpe: ScTypePolymorphicType =>
                val subst = ScSubstitutor.bind(ptpe.typeParameters)(UndefinedType(_))
                subst(ptpe.internalType.inferValueType)
              case tpe => tpe.inferValueType
            }

            val expr = Expression(withUndefParams, ref)

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
        case fun: ScFunction if paramClauses(fun).isEmpty =>
          return addExpectedTypeProblems()
        case fun: ScFun if fun.paramClauses.isEmpty =>
          return addExpectedTypeProblems()
        case _ =>
      }

      val functionLikeType = FunctionLikeType(ref)

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

    def checkSimpleApplication(
      typeParams: Seq[PsiTypeParameter]
    ): ApplicabilityCheckResult = {
      //if we are processing constructor proxies, take class type parameters into account
      val typeParamsWithCls = element match {
        case Constructor.ofClass(cls) => typeParams ++ cls.getTypeParameters.toSeq
        case fun: ScFunction          =>
          if (c.isExtensionCall) typeParams
          else                   fun.typeParametersWithExtension(c.exportedInExtension)
        case _ => typeParams
      }

      val typeArgCount         = typeArgElements.length
      val typeParamCount       = typeParamsWithCls.length
      val isAliasedConstructor = c.parentElement.exists(_.is[ScTypeAliasDefinition])

      if (!isAliasedConstructor && typeArgCount > 0 && typeArgCount != typeParamCount) {
        if (typeParamCount == 0) problems += DoesNotTakeTypeParameters
        else if (typeParamCount < typeArgCount)
          problems ++= typeArgElements.drop(typeParamCount).map(ExcessTypeArgument)
        else
          problems ++= typeParamsWithCls
            .drop(typeArgCount)
            .map(ptp => MissedTypeParameter(TypeParameter(ptp)))

        addExpectedTypeProblems()
      } else {
        val currentArgClause =
          if (c.nameArgForDynamic.nonEmpty && argClauseIdx == 0)
            Seq(createExpressionFromText("\"\"", ref))
          else argumentClauses.lift(actualArgClauseIdx).getOrElse(Seq.empty)

        val expectedTypeProblems = addExpectedTypeProblems()

        val expectedTypeSubst =
          expectedTypeProblems.constraints.substitutionBounds(canThrowSCE = false)

        val substitutorWithExpected =
          expectedTypeSubst.fold(substitutor)(bounds => substitutor.followed(bounds.substitutor))

        val argsApplicability =
          Compatibility.compatible(
            c,
            substitutorWithExpected,
            currentArgClause,
            checkWithImplicits,
            shapesOnly,
            ref,
            argClauseIdx
          )

        problems ++= argsApplicability.problems
        argsApplicability.copy(problems = problems.result())
      }
    }

    val result = element match {
      //objects
      case obj: ScObject =>
        if (argumentClauses.isEmpty) {
          expectedOption().map(_.removeAbstracts) match {
            case Some(FunctionType(_, _)) => problems += ExpectedTypeMismatch
            case Some(tp: ScType) if obj.isSAMEnabled =>
              SAMUtil.toSAMType(tp, obj) match {
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
      //Implicit Application
      case f: ScMethodLike if hasMalformedSignature(f) =>
        problems += MalformedDefinition(f.name)
        ApplicabilityCheckResult(problems.result())
      case fun: ScFunction if (typeArgElements.isEmpty ||
        typeArgElements.length == fun.typeParameters.length) && paramClauses(fun).length == 1 &&
        paramClauses(fun).head.isImplicit && //@TODO: multiple using clauses ???
        argumentClauses.isEmpty =>
        addExpectedTypeProblems()
      //eta expansion
      case (fun: ScTypeParametersOwner) & (_: PsiNamedElement)
        if (typeArgElements.isEmpty ||
          typeArgElements.length == fun.typeParameters.length) && argumentClauses.isEmpty =>
        checkFunctionReference(fun, fun.typeParameters.nonEmpty)
      case (fun: PsiTypeParameterListOwner) & (_: PsiNamedElement)
        if (typeArgElements.isEmpty ||
          typeArgElements.length == fun.getTypeParameters.length) && argumentClauses.isEmpty =>
        checkFunctionReference(fun, fun.getTypeParameters.nonEmpty)
      //simple application including empty application
      case tpOwner: ScTypeParametersOwner with PsiNamedElement     => checkSimpleApplication(tpOwner.typeParameters)
      case tpOwner: PsiTypeParameterListOwner with PsiNamedElement => checkSimpleApplication(tpOwner.getTypeParameters.toSeq)
      case _ =>
        if (typeArgElements.nonEmpty) problems += DoesNotTakeTypeParameters
        if (argumentClauses.nonEmpty) problems += DoesNotTakeParameters
        addExpectedTypeProblems()
    }

    if (result.problems.forall(_ == ExpectedTypeMismatch)) {
      val maybeResult = result.constraints match {
        case undefined @ ConstraintSystem(newSubstitutor) =>
          val typeParamIds = typeParameters.map(_.typeParamId).toSet

          var uSubst = undefined
          for (TypeParameter(tParam, _, lowerType, upperType) <- typeParameters) {
            val typeParamId = tParam.typeParamId

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
  }

  private def undefinedOrTypeArgsSubstitutor(
    element:                PsiElement,
    subst:                  ScSubstitutor,
    selfConstructorResolve: Boolean,
    typeArgElements:        Seq[ScTypeElement],
    isExtension:            Boolean,
    exportedInExtension:    Option[ScExtension]
  ): ScSubstitutor = {
    if (selfConstructorResolve) return ScSubstitutor.empty

    val maybeTypeParameters: Option[Seq[PsiTypeParameter]] = element match {
      case ScalaConstructor(cons)          => Option(cons.getConstructorTypeParameters)
      case cons @ Constructor.ofClass(cls) => Option((cls.getTypeParameters ++ cons.getTypeParameters).toSeq)
      case fun: ScFunction if !isExtension => Option(fun.typeParametersWithExtension(exportedInExtension))
      case t: ScTypeParametersOwner        => Option(t.typeParameters)
      case p: PsiTypeParameterListOwner    => Option(p.getTypeParameters.toSeq)
      case _                               => None
    }

    maybeTypeParameters match {
      case Some(typeParameters: Seq[PsiTypeParameter]) =>
        val follower =
          if (typeArgElements.nonEmpty && typeParameters.length == typeArgElements.length)
            ScSubstitutor.bind(typeParameters, typeArgElements)(_.calcType)
          else
            ScSubstitutor.bind(typeParameters)(UndefinedType(_))

        subst.followed(follower)
      case _ => subst
    }
  }

  private def filterShadowedDefinitions(input: Set[ScalaResolveResult]): Set[ScalaResolveResult] = {
    def hasParametersOrTypeParameters(srr: ScalaResolveResult, f: ScFunction): Boolean =
      f.parameterClausesWithExtension(srr.exportedInExtension).nonEmpty|| f.typeParametersWithExtension().nonEmpty

    //We want to leave only fields and properties from inherited classes, this is important, because
    //field in base class is shadowed by private field from inherited class
    val inputWithoutShadowed = input.filter { r =>
      r.element match {
        case f: ScFunction if hasParametersOrTypeParameters(r, f) => true
        case b: ScTypedDefinition =>
          b.nameContext match {
            case m: ScMember =>
              val cls1 = m.containingClass

              if (cls1 == null) true
              else {
                input.forall { r2 =>
                  r2.element match {
                    case f: ScFunction if hasParametersOrTypeParameters(r2, f) => true
                    case b2: ScTypedDefinition =>
                      b2.nameContext match {
                        case m2: ScMember =>
                          val cls2 = m2.containingClass
                          if (cls2 == null) true
                          else cls1.sameOrInheritor(cls2)
                        case _ => true
                      }
                    case _ => true
                  }
                }
              }
            case _ => true
          }
        case _ => true
      }
    }

    inputWithoutShadowed
  }

  private def candidates(
    proc:  MethodResolveProcessor,
    input: Set[ScalaResolveResult],
  ): Set[ScalaResolveResult] = {
    import proc.{candidates => _, _}

    val withoutShadowed = filterShadowedDefinitions(input)
    val maxArgClauseIdx = argumentClauses.size - 1

    @tailrec
    def candidatesForArgClause(
      prevClauseResults: Set[ScalaResolveResult],
      clauseIdx:         Int
    ): Set[ScalaResolveResult] = {
      //@TODO: since we care about multiple argument clauses in scala 3,
      //       we now need to expand apply methods at each step.
      //       (note: properly recalculate param clause index for apply methods)
      val expandedInput = prevClauseResults.flatMap(expandApplyOrUpdateMethod(_, proc, clauseIdx))

      val mappedShapesOnly = {
        val shapeResolved = checkResultsApplicability(
          proc,
          expandedInput,
          checkWithImplicits = false,
          useExpectedType    = true,
          args               = proc.argumentClauses,
          argClauseIdx       = clauseIdx,
          shapesOnly         = true
        )

        shapeResolved
      }

      val applicableToShape = mappedShapesOnly.filter {
        case (srr, _) => srr.isApplicable(withExpectedType = true)
      }

      val resultsForCurrentClause =
        if (isShapeResolve) {
          val res =
            if (applicableToShape.nonEmpty) applicableToShape
            else                            mappedShapesOnly

          res.map(_._1)
        } else {
          val preselected = {
            if (applicableToShape.isEmpty) expandedInput
            else                           applicableToShape
          }

          candidates(proc, preselected, clauseIdx)
        }

      val applicableForCurrentClause = resultsForCurrentClause.filter(_.isApplicable())

      if (applicableForCurrentClause.isEmpty)
        resultsForCurrentClause
      else if (useScala3OverloadingRules && applicableForCurrentClause.size > 1 && clauseIdx < maxArgClauseIdx)
        candidatesForArgClause(applicableForCurrentClause, clauseIdx + 1)
      else
        applicableForCurrentClause
    }

    candidatesForArgClause(withoutShadowed, 0)
  }

  private def candidates(
    proc:            MethodResolveProcessor,
    preselected:     Set[(ScalaResolveResult, Boolean)],
    argClauseIdx:    Int,
    useExpectedType: Boolean = true
  ): Set[ScalaResolveResult] = {
    import proc.{candidates => _, _}

    def applicableResults(cands: Set[(ScalaResolveResult, Boolean)]): Set[ScalaResolveResult] =
      cands.collect { case (srr, _) if srr.isApplicable(withExpectedType = useExpectedType) => srr }

    var mapped = checkResultsApplicability(
      proc,
      preselected,
      checkWithImplicits = false,
      useExpectedType    = useExpectedType,
      args               = proc.argumentClauses,
      argClauseIdx       = argClauseIdx
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
        args               = proc.argumentClauses,
        argClauseIdx       = argClauseIdx
      )

      filtered = applicableResults(mapped)
    }

    /**
     * Remove default parameters alternatives (in Scala 2 only)
     */
    if (filtered.size > 1 && !isShapeResolve && !useScala3OverloadingRules)
      filtered = filtered.filterNot(_.defaultParameterUsed)

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
            argClauseIdx       = argClauseIdx
          ).map { case (srr, _) =>
            srr.copy(tuplingUsed = true)
          }

        filtered = candsWithTupledArgs.filter(_.isApplicable())
      }
    }

    if (filtered.isEmpty) {
      //@TODO: does it make sense to retry every clause w/o expected type?
      if (useExpectedType && argClauseIdx == 0) {
        val withoutExpectedType = candidates(proc, preselected, argClauseIdx, useExpectedType = false)

        /**
         * If we can't get an applicable resolve result even w/o an expected type,
         * return mapped (with expected type), because it's more intuitive, when displaying errors to user.
         */
        if (withoutExpectedType.exists(_.isApplicable())) withoutExpectedType
        else                                                mapped.map(_._1)
      } else mapped.map(_._1)
    } else {
      val len =
        if (argumentClauses.isEmpty) 0
        else                         argumentClauses(argClauseIdx).length

      if (filtered.size == 1) filtered
      else
        MostSpecificUtil(ref, len, argClauseIdx).mostSpecificForResolveResult(filtered) match {
          case Some(r) => Set(r)
          case None    => filtered
        }
    }
  }

  private def expandApplyOrUpdateMethod(
    r:         ScalaResolveResult,
    proc:      MethodResolveProcessor,
    clauseIdx: Int
  ): Set[(ScalaResolveResult, Boolean)] = {
    import proc._

    val noExpansion = Set((r, false))

    def invocationInfo(e: PsiNamedElement): (ScSubstitutor, Seq[TypeParameter]) = e match {
      case _ if clauseIdx != 0 => (r.substitutor, Seq.empty)
      case owner: ScTypeParametersOwner if owner.typeParameters.nonEmpty =>
        val tparams = owner.typeParameters.map(TypeParameter(_))
        val subst   = ScSubstitutor.bind(tparams, typeArgElements)(_.calcType)
        (r.substitutor.followed(subst), tparams)
      case owner: PsiTypeParameterListOwner if owner.getTypeParameters.length > 0 =>
        val tparams = owner.getTypeParameters.instantiate
        val subst   = ScSubstitutor.bind(tparams, typeArgElements)(_.calcType)
        (r.substitutor.followed(subst), tparams)
      case _ => (r.substitutor, Seq.empty)
    }

    def applyOrUpdateMethodsFor(tp: ScType): Set[(ScalaResolveResult, Boolean)] = {
      val (substitutor, initialResolveTparams) = invocationInfo(r.element)
      val cleanTypeArguments                   = initialResolveTparams.nonEmpty

      val curriedTypeParams  =
        if (initialResolveTparams.nonEmpty && typeArgElements.isEmpty) initialResolveTparams
        else                                                           Seq.empty

      val callContext = ref.getContext match {
        case inv: MethodInvocation => inv.toOption
        case gen: ScGenericCall =>
          if (argumentClauses.nonEmpty) gen.getContext.asOptionOf[MethodInvocation]
          else                          gen.toOption
        case _ => None
      }

      val applyCandidates = callContext.toArray.flatMap(e =>
        e.resolveApplyOrUpdateMethod(
          e,
          substitutor(tp),
          shapesOnly    = isShapeResolve,
          stripTypeArgs = cleanTypeArguments,
          withImplicits = false
        )
      )

      if (applyCandidates.isEmpty)
        noExpansion
      else
        applyCandidates.view.collect {
          case rr if !accessibility || isAccessible(rr.element, ref) =>
            if (clauseIdx == 0) {
              val unresolvedTypeParameters =
                if (curriedTypeParams.nonEmpty)
                  Option(rr.unresolvedTypeParameters.fold(curriedTypeParams)(_ ++ curriedTypeParams))
                else
                  rr.unresolvedTypeParameters

              (rr.copy(
                innerResolveResult       = Option(r),
                parentElement            = r.element.toOption,
                importsUsed              = r.importsUsed,
                unresolvedTypeParameters = unresolvedTypeParameters
              ), cleanTypeArguments)
            } else
              rr -> false
        }.toSet
    }

    if (argumentClauses.isEmpty && typeArgElements.isEmpty || r.name == CommonNames.Apply)
      noExpansion
    else {
      val hasParams          = r.elementHasParameters
      val mismatchedTypeArgs = !r.elementHasTypeParameters && typeArgElements.nonEmpty

      r.element match {
        case synthetic: ScSyntheticFunction =>
          if (!hasParams && (argumentClauses.nonEmpty || mismatchedTypeArgs))
            applyOrUpdateMethodsFor(synthetic.polymorphicType())
          else
            noExpansion
        case f: PsiMethod =>
          if (!hasParams && (argumentClauses.nonEmpty || mismatchedTypeArgs))
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
    proc:               MethodResolveProcessor,
    expandedInput:      Set[(ScalaResolveResult, Boolean)],
    checkWithImplicits: Boolean,
    useExpectedType:    Boolean,
    args:               Seq[Seq[Expression]],
    argClauseIdx:       Int,
    shapesOnly:         Boolean = false,
  ): Set[(ScalaResolveResult, Boolean)] = {
    import proc.{argumentClauses => _, _}

    val resultBuilder = Set.newBuilder[(ScalaResolveResult, Boolean)]
    val iterator      = expandedInput.iterator

    while (iterator.hasNext) {
      val (cand, cleanTypeArgs) = iterator.next()

      val actualTypeArgs =
        if (cleanTypeArgs) Seq.empty
        else               typeArgElements

      val conformanceResult = problemsFor(
        getPlace,
        cand,
        checkWithImplicits,
        ref,
        args,
        actualTypeArgs,
        prevTypeInfo,
        if (useExpectedType) expectedOption else () => None,
        selfConstructorResolve = selfConstructorResolve,
        isUnderscore           = isUnderscore,
        shapesOnly             = shapesOnly,
        argClauseIdx           = argClauseIdx
      )

      val typeArgsSubst =
        if (actualTypeArgs.isEmpty) cand.substitutor
        else
          undefinedOrTypeArgsSubstitutor(
            cand.element,
            cand.substitutor,
            selfConstructorResolve,
            actualTypeArgs,
            cand.isExtensionCall,
            cand.exportedInExtension
          )


      val result = cand.copy(
        problems             = conformanceResult.problems,
        defaultParameterUsed = conformanceResult.defaultParameterUsed,
        resultUndef          = Option(conformanceResult.constraints),
        subst                = typeArgsSubst
      )

      resultBuilder += result -> cleanTypeArgs
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
}
