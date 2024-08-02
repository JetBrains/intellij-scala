package org.jetbrains.plugins.scala.lang.resolve.processor

import com.intellij.psi._
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiUtil
import org.jetbrains.plugins.scala.lang.psi.api.base._
import org.jetbrains.plugins.scala.lang.psi.api.base.types.ScTypeElement
import org.jetbrains.plugins.scala.lang.psi.api.expr._
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScFunction.CommonNames
import org.jetbrains.plugins.scala.lang.psi.api.statements._
import org.jetbrains.plugins.scala.lang.psi.api.statements.params.TypeParamIdOwner
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.{ScMember, ScObject, ScTemplateDefinition, ScTypeDefinition}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.{ScTypeParametersOwner, ScTypedDefinition}
import org.jetbrains.plugins.scala.lang.psi.impl.ScPackageImpl
import org.jetbrains.plugins.scala.lang.psi.impl.expr.ApplyOrUpdateInvocation
import org.jetbrains.plugins.scala.lang.psi.impl.toplevel.synthetic.ScSyntheticFunction
import org.jetbrains.plugins.scala.lang.psi.types.Compatibility.{ConformanceExtResult, Expression}
import org.jetbrains.plugins.scala.lang.psi.types._
import org.jetbrains.plugins.scala.lang.psi.types.api._
import org.jetbrains.plugins.scala.lang.psi.types.api.designator.{ScDesignatorType, ScProjectionType}
import org.jetbrains.plugins.scala.lang.psi.types.nonvalue.{ScMethodType, ScTypePolymorphicType}
import org.jetbrains.plugins.scala.lang.psi.types.recursiveUpdate.ScSubstitutor
import org.jetbrains.plugins.scala.lang.psi.types.result._
import org.jetbrains.plugins.scala.lang.resolve.MethodTypeProvider._
import org.jetbrains.plugins.scala.lang.resolve.ScalaResolveState.ResolveStateExt
import org.jetbrains.plugins.scala.lang.resolve.{ResolveTargets, ScalaResolveResult, StdKinds}
import org.jetbrains.plugins.scala.project.{ProjectContext, ProjectPsiElementExt}
import org.jetbrains.plugins.scala.util.SAMUtil

class MethodResolveProcessor(override val ref: PsiElement,
                             val refName: String,
                             var argumentClauses: List[Seq[Expression]],
                             val typeArgElements: Seq[ScTypeElement],
                             val prevTypeInfo: Seq[TypeParameter],
                             kinds: Set[ResolveTargets.Value] = StdKinds.methodRef,
                             val expectedOption: () => Option[ScType] = () => None,
                             val isUnderscore: Boolean = false,
                             var isShapeResolve: Boolean = false,
                             val constructorResolve: Boolean = false,
                             val enableTupling: Boolean = false,
                             var noImplicitsForArgs: Boolean = false,
                             val selfConstructorResolve: Boolean = false,
                             val nameArgForDynamic: Option[String] = None)
  extends ResolveProcessor(kinds, ref, refName) {

  def isDynamic: Boolean = nameArgForDynamic.nonEmpty

  override protected def execute(namedElement: PsiNamedElement)
                                (implicit state: ResolveState): Boolean = {

    def implFunction: Option[ScalaResolveResult] = state.implicitConversion

    def implType: Option[ScType] = state.implicitType

    def isNamedParameter: Boolean = state.isNamedParameter
    def fromType: Option[ScType] = state.fromType
    def unresolvedTypeParameters: Option[Seq[TypeParameter]] = state.unresolvedTypeParams
    def renamed: Option[String] = state.renamed
    def forwardReference: Boolean = state.isForwardRef
    def extensionMethod: Boolean = state.isExtensionMethod
    def extensionContext: Option[ScExtension] = state.extensionContext
    def intersectedReturnType: Option[ScType] = state.intersectedReturnType
    def importsUsed = state.importsUsed
    def exportedIn = state.exportedIn

    if (nameMatches(namedElement) || constructorResolve) {
      val accessible = isNamedParameter || isAccessible(namedElement, ref)
      if (accessibility && !accessible) return true

      val s = state.substitutorWithThisType(namedElement.findContextOfType(classOf[PsiClass]).orNull)
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
          exportedIn               = exportedIn
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

  override def candidatesS: Set[ScalaResolveResult] = {
    if (isDynamic) {
      collectCandidates(super.candidatesS.map(_.copy(nameArgForDynamic = nameArgForDynamic))).filter(_.isApplicable())
    } else {
      val superCandidates = super.candidatesS
      val res = collectCandidates(superCandidates)
      res
    }
  }

  private def collectCandidates(input: Set[ScalaResolveResult]): Set[ScalaResolveResult] = {
    if (input.isEmpty) return input
    if (!isShapeResolve && enableTupling && argumentClauses.nonEmpty) {
      isShapeResolve = true
      val cand1 = MethodResolveProcessor.candidates(this, input)
      if (!isDynamic && (cand1.isEmpty || cand1.forall(_.tuplingUsed))) {
        //tupling ok
        isShapeResolve = false
        val oldArg = argumentClauses
        val tpl = ScalaPsiUtil.tupled(argumentClauses.head, ref)
        if (tpl.isEmpty) {
          return MethodResolveProcessor.candidates(this, input)
        }
        argumentClauses = tpl.toList
        val res = MethodResolveProcessor.candidates(this, input)
        argumentClauses = oldArg
        if (res.forall(!_.isApplicable())) {
          return MethodResolveProcessor.candidates(this, input)
        }
        res.map(r => r.copy(tuplingUsed = true))
      } else {
        isShapeResolve = false
        MethodResolveProcessor.candidates(this, input)
      }
    } else {
      MethodResolveProcessor.candidates(this, input)
    }
  }
}

object MethodResolveProcessor {
  private def problemsFor(
    c:                      ScalaResolveResult,
    checkWithImplicits:     Boolean,
    ref:                    PsiElement,
    argumentClauses:        List[Seq[Expression]],
    typeArgElements:        Seq[ScTypeElement],
    selfConstructorResolve: Boolean,
    prevTypeInfo:           Seq[TypeParameter],
    expectedOption:         () => Option[ScType],
    isUnderscore:           Boolean,
    isShapeResolve:         Boolean
  ): ConformanceExtResult = {

    implicit val ctx: ProjectContext = c.element

    val problems = Seq.newBuilder[ApplicabilityProblem]
    val element  = c.element
    val s        = c.substitutor

    val elementsForUndefining = element match {
      case ScalaConstructor(_) if !selfConstructorResolve => Seq(c.getActualElement)
      case Constructor(_)                                 => Seq(c.getActualElement, element).distinct
      case _                                              => Seq(element) //do not
    }

    val iterator = elementsForUndefining.iterator
    var tempSubstitutor: ScSubstitutor = ScSubstitutor.empty
    while (iterator.hasNext) {
      val element = iterator.next()

      tempSubstitutor = tempSubstitutor.followed(
        undefinedSubstitutor(
          element,
          s,
          selfConstructorResolve,
          typeArgElements,
          c.isExtensionCall,
          c.exportedInExtension
        )
      )
    }

    val unresovledTps = c.unresolvedTypeParameters.getOrElse(Seq.empty)

    val substitutor =
      tempSubstitutor.followed(ScSubstitutor.bind(prevTypeInfo ++ unresovledTps)(UndefinedType(_)))

    val typeParameters: Seq[TypeParameter] = prevTypeInfo ++ (element match {
      case ScalaConstructor(cons) => cons.getConstructorTypeParameters.map(TypeParameter(_))
      case cons @ Constructor.ofClass(cls) =>
        (cls.getTypeParameters ++ cons.getTypeParameters).toSeq.map(TypeParameter(_))
      case fun: ScFunction => fun.typeParameters.map(TypeParameter(_))
      case fun: PsiMethod  => fun.getTypeParameters.map(TypeParameter(_)).toSeq
      case _               => Seq.empty
    })

    def addExpectedTypeProblems(): ConformanceExtResult = {
      if (expectedOption().isEmpty) {
        val problemsSeq = problems.result()
        return ConformanceExtResult(problemsSeq)
      }

      val expected = expectedOption().get

      val retType: ScType = element match {
        case cons @ ScalaConstructor.in(td: ScTypeDefinition) =>
          val bindTypeParamsSubst = ScSubstitutor.bind(td.typeParameters, cons.getConstructorTypeParameters)(TypeParameterType(_))
          substitutor(bindTypeParamsSubst(td.`type`().getOrNothing))
        case Constructor.ofClass(cls) =>
          substitutor(ScalaPsiUtil.constructTypeForPsiClass(cls)((tp, _) => TypeParameterType(tp)))
        case f: ScFunction
          if f.paramClauses.clauses.length > 1 &&
            !f.paramClauses.clauses(1).isImplicit =>
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

      ConformanceExtResult(problems.result(), conformance.constraints)
    }

    def checkFunction(fun: PsiNamedElement, isPolymorphic: Boolean): ConformanceExtResult = {
      def default(): ConformanceExtResult = {
        fun match {
          case fun: ScFunction if fun.paramClauses.clauses.isEmpty ||
            fun.paramClauses.clauses.head.parameters.isEmpty ||
            isUnderscore => ConformanceExtResult(problems.result())
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

      def checkEtaExpandedReference(fun: PsiNamedElement, pt: ScType): ConformanceExtResult = {
        val maybeMethodType = fun match {
          case m: PsiMethod => m.methodTypeProvider(ref.elementScope).polymorphicType().toOption
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
          case ConstraintsResult.Left => ConformanceExtResult(Seq(ExpectedTypeMismatch))
          case cs: ConstraintSystem   => ConformanceExtResult(problems.result(), cs)
        }
      }

      fun match {
        case fun: ScFunction if fun.paramClauses.clauses.isEmpty =>
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
    ): ConformanceExtResult = {
      //if we are processing constructor proxies, take class type parameters into account
      val typeParamsWithCls = element match {
        case Constructor.ofClass(cls) => typeParams ++ cls.getTypeParameters.toSeq
        case _                        => typeParams
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
        val args                 = argumentClauses.headOption.toList
        val expectedTypeProblems = addExpectedTypeProblems()

        val expectedTypeSubst =
          expectedTypeProblems.constraints.substitutionBounds(canThrowSCE = false)

        val substitutorWithExpected =
          expectedTypeSubst.fold(substitutor)(bounds => substitutor.followed(bounds.substitutor))

        val argsConformance =
          Compatibility.compatible(
            c,
            substitutorWithExpected,
            args,
            checkWithImplicits,
            isShapeResolve,
            ref
          )

        problems ++= argsConformance.problems
        argsConformance.copy(problems = problems.result())
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
        ConformanceExtResult(problems.result())
      case _: PsiClass    => ConformanceExtResult(problems.result())
      case _: ScTypeAlias => ConformanceExtResult(problems.result())
      //Implicit Application
      case f: ScMethodLike if hasMalformedSignature(f) =>
        problems += MalformedDefinition(f.name)
        ConformanceExtResult(problems.result())
      case fun: ScFunction if (typeArgElements.isEmpty ||
        typeArgElements.length == fun.typeParameters.length) && fun.paramClauses.clauses.length == 1 &&
        fun.paramClauses.clauses.head.isImplicitOrUsing && //@TODO: multiple using clauses ???
        argumentClauses.isEmpty =>
        addExpectedTypeProblems()
      //eta expansion
      case (fun: ScTypeParametersOwner) & (_: PsiNamedElement)
        if (typeArgElements.isEmpty ||
          typeArgElements.length == fun.typeParameters.length) && argumentClauses.isEmpty =>
        checkFunction(fun, fun.typeParameters.nonEmpty)
      case (fun: PsiTypeParameterListOwner) & (_: PsiNamedElement)
        if (typeArgElements.isEmpty ||
          typeArgElements.length == fun.getTypeParameters.length) && argumentClauses.isEmpty =>
        checkFunction(fun, fun.getTypeParameters.nonEmpty)
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
              s(newSubstitutor(lowerType)) match {
                case lower if !lower.hasRecursiveTypeParameters(typeParamIds) =>
                  uSubst = uSubst.withLower(typeParamId, lower)
                    .withTypeParamId(typeParamId)
                case _ =>
              }
            }

            if (!upperType.isAny) {
              s(newSubstitutor(upperType)) match {
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

  def undefinedSubstitutor(
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

  def candidates(
    proc:            MethodResolveProcessor,
    _input:          Set[ScalaResolveResult],
    useExpectedType: Boolean = true
  ): Set[ScalaResolveResult] = {
    import proc.{candidates => _, _}

    def hasParametersOrTypeParameters(f: ScFunction): Boolean =
      f.parameterClausesWithExtension().nonEmpty|| f.typeParametersWithExtension().nonEmpty
    //We want to leave only fields and properties from inherited classes, this is important, because
    //field in base class is shadowed by private field from inherited class
    val input: Set[ScalaResolveResult] = _input.filter { r =>
      r.element match {
        case f: ScFunction if hasParametersOrTypeParameters(f) => true
        case b: ScTypedDefinition =>
          b.nameContext match {
            case m: ScMember =>
              val clazz1: ScTemplateDefinition = m.containingClass
              if (clazz1 == null) true
              else {
                _input.forall { r2 =>
                  r2.element match {
                    case f: ScFunction if hasParametersOrTypeParameters(f) => true
                    case b2: ScTypedDefinition =>
                      b2.nameContext match {
                        case m2: ScMember =>
                          val clazz2: ScTemplateDefinition = m2.containingClass
                          if (clazz2 == null) true
                          else clazz1.sameOrInheritor(clazz2)
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

    var mapped   = checkResultsApplicability(proc, input, checkWithImplicits = false, useExpectedType)
    var filtered = mapped.filter(_.isApplicable(withExpectedType = useExpectedType))

    if (filtered.isEmpty && !noImplicitsForArgs) {
      //check with implicits
      mapped   = checkResultsApplicability(proc, input, checkWithImplicits = true, useExpectedType = useExpectedType)
      filtered = mapped.filter(_.isApplicable(withExpectedType = useExpectedType))
    }

    //remove default parameters alternatives (in Scala 2 only)
    if (filtered.size > 1 && !isShapeResolve && !ref.isInScala3File)
      filtered = filtered.filterNot(_.defaultParameterUsed)

    if (isShapeResolve) {
      if (filtered.isEmpty) {
        if (enableTupling && !useExpectedType) {
          val filteredWithAutoTupling = input.filter {
              _.element match {
                case fun: ScFun if fun.paramClauses.nonEmpty =>
                  fun.paramClauses.head.length == 1
                case fun: ScFunction if fun.paramClauses.clauses.nonEmpty =>
                  fun.paramClauses.clauses.head.parameters.length == 1
                case p: ScPrimaryConstructor if p.parameterList.clauses.nonEmpty =>
                  p.parameterList.clauses.head.parameters.length == 1
                case m: PsiMethod => m.parameters.length == 1
                case _            => false
              }
          }.map(r => r.copy(tuplingUsed = true))

          if (filteredWithAutoTupling.nonEmpty) return filteredWithAutoTupling
        }
      } else return filtered
    }

    if (filtered.isEmpty && mapped.isEmpty) input.map(r => r.copy(notCheckedResolveResult = true))
    else if (filtered.isEmpty) {
      if (useExpectedType) {
        val withoutExpectedType = candidates(proc, input, useExpectedType = false)

        //If we can't get an applicable resolve result even w/o an expectedType,
        //return mapped (with expected type), just because it's more intuitive, when displaying errors to the user.
        if (withoutExpectedType.exists(_.isApplicable())) withoutExpectedType
        else                                              mapped
      } else mapped
    } else {
      val len =
        if (argumentClauses.isEmpty) 0
        else                         argumentClauses.head.length

      if (filtered.size == 1) filtered
      else
        MostSpecificUtil(ref, len).mostSpecificForResolveResult(filtered) match {
          case Some(r) => Set(r)
          case None    => filtered
        }
    }
  }

  private def expandApplyOrUpdateMethod(
    r:    ScalaResolveResult,
    proc: MethodResolveProcessor
  ): Set[(ScalaResolveResult, Boolean)] = {
    import proc._

    def invocationInfo(e: PsiNamedElement): (ScSubstitutor, Boolean) = e match {
      case owner: ScTypeParametersOwner if owner.typeParameters.nonEmpty =>
        val tparams = owner.typeParameters.map(TypeParameter(_))

        val subst =
          if (typeArgElements.nonEmpty && tparams.length == typeArgElements.length)
            ScSubstitutor.bind(tparams, typeArgElements)(_.calcType)
          else ScSubstitutor.empty

        (r.substitutor.followed(subst), true)
      case owner: PsiTypeParameterListOwner if owner.getTypeParameters.length > 0 =>
        val tparams = owner.getTypeParameters.instantiate

        val subst =
          if (typeArgElements.nonEmpty && tparams.length == typeArgElements.length)
            ScSubstitutor.bind(tparams, typeArgElements)(_.calcType)
          else ScSubstitutor.empty

        (r.substitutor.followed(subst), true)
      case _ => (r.substitutor, false)
    }

    def applyMethodsFor(tp: ScType): Set[(ScalaResolveResult, Boolean)] = {
      val (substitutor, cleanTypeArguments) = invocationInfo(r.element)

      def applyResolver(e: PsiElement): Option[ApplyOrUpdateInvocation] = {
        def methodCallApplyResolver(mc: MethodInvocation): ApplyOrUpdateInvocation =
          ApplyOrUpdateInvocation(mc, substitutor(tp), isDynamic, stripTypeArgs = cleanTypeArguments)

        e match {
          case mc: MethodInvocation => methodCallApplyResolver(mc).toOption
          case gc: ScGenericCall    =>
            gc.getContext match {
              case mc: MethodInvocation => methodCallApplyResolver(mc).toOption
              case _                    => ApplyOrUpdateInvocation(gc, substitutor(tp), stripTypeArgs = cleanTypeArguments).toOption
            }
          case _ => None
        }
      }

      val result = applyResolver(ref.getContext) match {
        case Some(proc: ApplyOrUpdateInvocation) =>
          val cands = proc.collectCandidates(isShape = isShapeResolve, withImplicits = false)

          if (cands.isEmpty) Set((r, false))
          else               cands.view.collect {
            case rr if !accessibility || isAccessible(rr.element, ref) =>
              (rr.copy(innerResolveResult = Option(r), parentElement = r.element.toOption), cleanTypeArguments)
          }.toSet
        case _ => Set((r, false))
      }
      result
    }

    if (r.name == CommonNames.Apply) {
      //This is the case when previous shapeResolve has already extracted an apply method
      //we still need to calculate if type args are relevant or not.
      val originalElement = r.innerResolveResult.getOrElse(r).element
      val (_, cleanTypeArgs) = invocationInfo(originalElement)
      Set((r, cleanTypeArgs))
    } else if (argumentClauses.isEmpty && typeArgElements.isEmpty)
      Set((r, false))
    else {
      val hasParams          = r.elementHasParameters
      val mismatchedTypeArgs = !r.elementHasTypeParameters && typeArgElements.nonEmpty

      r.element match {
        case synthetic: ScSyntheticFunction =>
          if (!hasParams && (argumentClauses.nonEmpty || mismatchedTypeArgs))
            applyMethodsFor(synthetic.polymorphicType())
          else
            Set((r, false))
        case f: PsiMethod =>
          if (!hasParams && (argumentClauses.nonEmpty || mismatchedTypeArgs))
            applyMethodsFor(
              f.methodTypeProvider(proc.ref.elementScope)
               .polymorphicType(dropExtensionClauses = r.isExtensionCall)
            )
          else
            Set((r, false))
        case b: ScTypedDefinition =>
          val tpe =
            if (b.isStable)
              r.fromType match {
                case Some(tp) => ScProjectionType(tp, b).toOption
                case None     => ScDesignatorType(b).toOption
              }
            else b.`type`().toOption

          tpe.map(applyMethodsFor).getOrElse(Set.empty)
        case b: PsiField => // See SCL-3055
          applyMethodsFor(b.getType.toScType())
        case _ => Set((r, false))
      }
    }
  }

  private def checkResultsApplicability(
    proc:               MethodResolveProcessor,
    input:              Set[ScalaResolveResult],
    checkWithImplicits: Boolean,
    useExpectedType:    Boolean
  ): Set[ScalaResolveResult] = {
    import proc._

    val expanded = input.flatMap(expandApplyOrUpdateMethod(_, proc))
    val builder  = Set.newBuilder[ScalaResolveResult]
    val iterator = expanded.iterator

    //problemsFor make all the work, wrapping it in scala collection API adds 9 unnecessary methods to the stacktrace
    while (iterator.hasNext) {
      val (r, undefineTypeParams) = iterator.next()
      val typeArgElems =
        if (undefineTypeParams) Seq.empty
        else                    typeArgElements

      val conformanceResult = problemsFor(
        r,
        checkWithImplicits,
        ref,
        argumentClauses,
        typeArgElems,
        selfConstructorResolve,
        prevTypeInfo,
        if (useExpectedType) expectedOption else () => None,
        isUnderscore,
        isShapeResolve
      )

      val result = r.copy(
        problems             = conformanceResult.problems,
        defaultParameterUsed = conformanceResult.defaultParameterUsed,
        resultUndef          = Option(conformanceResult.constraints)
      )

      builder += result
    }

    builder.result()
  }

  //Signature has repeated param, which is not the last one
  private def hasMalformedSignature(ml: ScMethodLike) = ml.parameterList.clauses.exists {
    _.parameters.dropRight(1).exists(_.isRepeatedParameter)
  }
}
