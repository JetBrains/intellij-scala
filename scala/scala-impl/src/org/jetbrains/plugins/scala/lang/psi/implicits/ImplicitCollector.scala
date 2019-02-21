package org.jetbrains.plugins.scala
package lang.psi.implicits

import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.project.Project
import com.intellij.psi._
import com.intellij.psi.util.PsiTreeUtil
import org.jetbrains.plugins.scala.caches.RecursionManager
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.macros.evaluator.{MacroContext, ScalaMacroEvaluator}
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiUtil
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiUtil.strictlyOrderedByContext
import org.jetbrains.plugins.scala.lang.psi.api.InferUtil
import org.jetbrains.plugins.scala.lang.psi.api.InferUtil.SafeCheckException
import org.jetbrains.plugins.scala.lang.psi.api.base.ScFieldId
import org.jetbrains.plugins.scala.lang.psi.api.base.patterns.{ScBindingPattern, ScCaseClause}
import org.jetbrains.plugins.scala.lang.psi.api.statements._
import org.jetbrains.plugins.scala.lang.psi.api.statements.params.{ScClassParameter, ScParameter}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.{ScMember, ScObject}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.{ScNamedElement, ScTypedDefinition}
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiManager
import org.jetbrains.plugins.scala.lang.psi.implicits.ExtensionConversionHelper.extensionConversionCheck
import org.jetbrains.plugins.scala.lang.psi.implicits.ImplicitCollector._
import org.jetbrains.plugins.scala.lang.psi.types._
import org.jetbrains.plugins.scala.lang.psi.types.api._
import org.jetbrains.plugins.scala.lang.psi.types.api.designator._
import org.jetbrains.plugins.scala.lang.psi.types.nonvalue.{ScMethodType, ScTypePolymorphicType}
import org.jetbrains.plugins.scala.lang.psi.types.recursiveUpdate.AfterUpdate.{ProcessSubtypes, ReplaceWith}
import org.jetbrains.plugins.scala.lang.psi.types.recursiveUpdate.ScSubstitutor
import org.jetbrains.plugins.scala.lang.psi.types.result._
import org.jetbrains.plugins.scala.lang.resolve._
import org.jetbrains.plugins.scala.lang.resolve.processor.{BaseProcessor, MostSpecificUtil}
import org.jetbrains.plugins.scala.project.ProjectContext
import org.jetbrains.plugins.scala.settings.ScalaProjectSettings
import org.jetbrains.plugins.scala.util.BetterMonadicForSupport.Implicit0Binding
import org.jetbrains.plugins.scala.project._

import scala.collection.Set

object ImplicitCollector {

  def cache(project: Project): ImplicitCollectorCache = ScalaPsiManager.instance(project).implicitCollectorCache

  sealed trait ImplicitResult

  sealed trait FullInfoResult extends ImplicitResult

  case object NoResult extends ImplicitResult

  case object OkResult extends FullInfoResult
  case object ImplicitParameterNotFoundResult extends FullInfoResult
  case object DivergedImplicitResult extends FullInfoResult
  case object CantInferTypeParameterResult extends FullInfoResult

  case object TypeDoesntConformResult extends ImplicitResult
  case object BadTypeResult extends ImplicitResult
  case object CantFindExtensionMethodResult extends ImplicitResult
  case object UnhandledResult extends ImplicitResult
  case object FunctionForParameterResult extends ImplicitResult

  case class ImplicitState(place: PsiElement,
                           tp: ScType,
                           expandedTp: ScType,
                           coreElement: Option[ScNamedElement],
                           isImplicitConversion: Boolean,
                           searchImplicitsRecursively: Int,
                           extensionData: Option[ExtensionConversionData],
                           fullInfo: Boolean,
                           previousRecursionState: Option[ImplicitsRecursionGuard.RecursionMap]) {

    def presentableTypeText: String = tp.presentableText(place)
  }

}

/**
 * @param place        The call site
 * @param tp           Search for an implicit definition of this type. May have type variables.
 *
 * User: Alexander Podkhalyuzin
 * Date: 23.11.2009
 */
class ImplicitCollector(place: PsiElement,
                        tp: ScType,
                        expandedTp: ScType,
                        coreElement: Option[ScNamedElement],
                        isImplicitConversion: Boolean,
                        searchImplicitsRecursively: Int = 0,
                        extensionData: Option[ExtensionConversionData] = None,
                        fullInfo: Boolean = false,
                        previousRecursionState: Option[ImplicitsRecursionGuard.RecursionMap] = None) {
  def this(state: ImplicitState) {
    this(state.place, state.tp, state.expandedTp, state.coreElement, state.isImplicitConversion,
       state.searchImplicitsRecursively, state.extensionData, state.fullInfo, state.previousRecursionState)
  }

  lazy val collectorState: ImplicitState = ImplicitState(place, tp, expandedTp, coreElement, isImplicitConversion,
    searchImplicitsRecursively, extensionData, fullInfo, Some(ImplicitsRecursionGuard.currentMap))

  private val project = place.getProject
  private implicit def ctx: ProjectContext = project

  private val clazz: Option[PsiClass] = tp.extractClass
  private lazy val possibleScalaFunction: Option[Int] = clazz.flatMap(possibleFunctionN)

  private val mostSpecificUtil: MostSpecificUtil = MostSpecificUtil(place, 1)

  private def isExtensionConversion: Boolean = extensionData.isDefined

  def collect(): Seq[ScalaResolveResult] = {
    def calc(): Seq[ScalaResolveResult] = {
      clazz match {
        case Some(c) if InferUtil.tagsAndManifists.contains(c.qualifiedName) => return Seq.empty
        case _                                                               =>
      }

      ProgressManager.checkCanceled()

      if (fullInfo) {
        val visible = visibleNamesCandidates
        val fromNameCandidates = collectFullInfo(visible)

        val allCandidates =
          if (fromNameCandidates.exists(_.implicitReason == OkResult)) fromNameCandidates
          else {
            fromNameCandidates ++ collectFullInfo(fromTypeCandidates.diff(visible))
          }

        //todo: should we also compare types like in MostSpecificUtil.isAsSpecificAs ?
        allCandidates.sortWith(mostSpecificUtil.isInMoreSpecificClass)
      }
      else {
        val implicitCollectorCache = ImplicitCollector.cache(project)
        implicitCollectorCache.get(place, tp) match {
          case Some(cached) if !fullInfo => return cached
          case _ =>
        }
        val stackStamp = RecursionManager.markStack()

        val firstCandidates = compatible(visibleNamesCandidates)
        val result =
          if (firstCandidates.exists(_.isApplicable())) firstCandidates
          else {
            val secondCandidates = compatible(fromTypeCandidates)
            if (secondCandidates.nonEmpty) secondCandidates else firstCandidates
          }

        if (!isExtensionConversion && stackStamp.mayCacheNow())
          implicitCollectorCache.put(place, tp, result)

        result
      }
    }

    previousRecursionState match {
      case Some(m) =>
        val currentMap = ImplicitsRecursionGuard.currentMap
        try {
          ImplicitsRecursionGuard.setRecursionMap(m)
          calc()
        } finally {
          ImplicitsRecursionGuard.setRecursionMap(currentMap)
        }
      case _ => calc()
    }
  }

  private def visibleNamesCandidates: Set[ScalaResolveResult] =
    new ImplicitParametersProcessor(place, withoutPrecedence = false)
      .candidatesByPlace

  private def fromTypeCandidates =
    new ImplicitParametersProcessor(place, withoutPrecedence = true)
      .candidatesByType(expandedTp)

  private def compatible(candidates: Set[ScalaResolveResult]): Seq[ScalaResolveResult] = {
    //implicits found without local type inference have higher priority
    val withoutLocalTypeInference = collectCompatibleCandidates(candidates, withLocalTypeInference = false)

    val compatible =
      if (withoutLocalTypeInference.nonEmpty) withoutLocalTypeInference
      else collectCompatibleCandidates(candidates, withLocalTypeInference = true)


    mostSpecificUtil.mostSpecificForImplicitParameters(compatible) match {
      case Some(r) => Seq(r)
      case _ => compatible.toSeq.map(_._1)
    }
  }

  private def collectFullInfo(candidates: Set[ScalaResolveResult]): Seq[ScalaResolveResult] = {
    val allCandidates =
      candidates.flatMap(c => checkCompatible(c, withLocalTypeInference = false)) ++
        candidates.flatMap(c => checkCompatible(c, withLocalTypeInference = true))
    val afterExtensionPredicate = allCandidates.flatMap(applyExtensionPredicate)

    afterExtensionPredicate
      .withFilter(_._1.implicitReason.isInstanceOf[FullInfoResult])
      .map(_._1).toSeq
  }

  private final class ImplicitParametersProcessor(override val getPlace: PsiElement,
                                                  override protected val withoutPrecedence: Boolean)
    extends ImplicitProcessor(getPlace, withoutPrecedence) {

    override protected def execute(namedElement: PsiNamedElement)
                                  (implicit state: ResolveState): Boolean = {
      def addResultForElement(): Boolean = {
        val subst = state.get(BaseProcessor.FROM_TYPE_KEY) match {
          case null => getSubst(state)
          case t => getSubst(state).followUpdateThisType(t)
        }
        addResult(new ScalaResolveResult(namedElement, subst, getImports(state), implicitSearchState = Some(collectorState)))
      }

      namedElement match {
        case p: ScParameter if p.isImplicitParameter =>
          p match {
            case c: ScClassParameter if !isAccessible(c) => return true
            case _ =>
          }
          addResultForElement()
        case member: ScMember if member.hasModifierProperty("implicit") =>
          if (isAccessible(member)) addResultForElement()
        case Implicit0Binding() => addResultForElement() /** See [[org.jetbrains.plugins.scala.util.BetterMonadicForSupport]] */
        case _: ScBindingPattern | _: ScFieldId =>
          val member = ScalaPsiUtil.getContextOfType(namedElement, true, classOf[ScValue], classOf[ScVariable]) match {
            case m: ScMember if m.hasModifierProperty("implicit") => m
            case _ => return true
          }
          if (isAccessible(member)) addResultForElement()
        case _ =>
      }

      true
    }

    override def candidatesS: Set[ScalaResolveResult] =
      super.candidatesS.filterNot(c => lowerInFileWithoutType(c) || isContextAncestor(c))

    private def isAccessible(member: ScMember): Boolean = {
      isPredefPriority || (member match {
        case fun: ScFunction => checkFunctionIsEligible(fun) && ResolveUtils.isAccessible(member, getPlace)
        case _ => ResolveUtils.isAccessible(member, getPlace)
      })
    }

    private def lowerInFileWithoutType(c: ScalaResolveResult) = {
      def contextFile(e: PsiElement) = Option(PsiTreeUtil.getContextOfType(e, classOf[PsiFile]))

      def lowerInFile(e: PsiElement) = {
        val resolveFile = contextFile(e)
        val placeFile = contextFile(getPlace)

        resolveFile == placeFile && strictlyOrderedByContext(before = getPlace, after = e, placeFile)
      }

      c.getElement match {
        case fun: ScFunction if fun.returnTypeElement.isEmpty => lowerInFile(fun)
        case pattern@ScalaPsiUtil.inNameContext(pd: ScPatternDefinition) if pd.typeElement.isEmpty => lowerInFile(pattern)
        case _ => false
      }
    }

    private def isContextAncestor(c: ScalaResolveResult): Boolean = {
      val nameContext = ScalaPsiUtil.nameContext(c.element)
      nameContext match {
        case _: ScCaseClause if getPlace.betterMonadicForEnabled => false
        case _                                                   => PsiTreeUtil.isContextAncestor(nameContext, getPlace, false)
      }
    }
  }

  private def possibleFunctionN(clazz: PsiClass): Option[Int] =
    clazz.qualifiedName match {
      case "java.lang.Object" | "scala.ScalaObject" => Some(-1)
      case name =>
        val paramsNumber = name.stripPrefix(FunctionType.TypeName)
        if (paramsNumber.nonEmpty && paramsNumber.forall(_.isDigit)) Some(paramsNumber.toInt)
        else None
    }

  def checkCompatible(c: ScalaResolveResult, withLocalTypeInference: Boolean, checkFast: Boolean = false): Option[Candidate] = {
    ProgressManager.checkCanceled()

    c.element match {
      case fun: ScFunction =>
        if (!fun.hasTypeParameters && withLocalTypeInference) return None

        //scala.Predef.$conforms should be excluded
        if (isImplicitConversion && isPredefConforms(fun)) return None

        //to avoid checking implicit functions in case of simple implicit parameter search
        val hasNonImplicitClause = fun.effectiveParameterClauses.exists(!_.isImplicit)
        if (hasNonImplicitClause) {
          val clause = fun.paramClauses.clauses.head
          val paramsCount = clause.parameters.size
          if (!possibleScalaFunction.exists(x => x == -1 || x == paramsCount)) {
            return reportWrong(c, c.substitutor, FunctionForParameterResult, Seq(WrongTypeParameterInferred))
          }
        }

        if (isExtensionConversion && !fullInfo) {
          val applicableParameters =
            checkFunctionByType(c, withLocalTypeInference, checkFast, noReturnType = true).isDefined

          if (applicableParameters)
            checkFunctionByType(c, withLocalTypeInference, checkFast, noReturnType = false)
          else None
        }
        else checkFunctionByType(c, withLocalTypeInference, checkFast, noReturnType = false)

      case _ =>
        if (withLocalTypeInference) None //only functions may have local type inference
        else simpleConformanceCheck(c)
    }
  }

  def collectCompatibleCandidates(candidates: Set[ScalaResolveResult], withLocalTypeInference: Boolean): Set[Candidate] = {
    var filteredCandidates = Set.empty[ScalaResolveResult]

    val iterator = candidates.iterator
    while (iterator.hasNext) {
      val c = iterator.next()
      val plausible = checkCompatible(c, withLocalTypeInference, checkFast = true).isDefined
      if (plausible) filteredCandidates += c
    }

    var results: Set[Candidate] = Set()

    while (filteredCandidates.nonEmpty) {
      val next = mostSpecificUtil.nextMostSpecific(filteredCandidates)
      next match {
        case Some(c) =>
          filteredCandidates = filteredCandidates - c
          val compatible = checkCompatible(c, withLocalTypeInference)
          val afterExtensionPredicate = compatible.flatMap(applyExtensionPredicate)
          afterExtensionPredicate.foreach { case (r, s) =>
            if (!r.problems.contains(WrongTypeParameterInferred)) {
              val notMoreSpecific = mostSpecificUtil.notMoreSpecificThan(r)
              filteredCandidates = filteredCandidates.filter(notMoreSpecific)
              //this filter was added to make result deterministic
              results = results.filter(c => notMoreSpecific(c._1))
              results += ((r, s))
            }
          }
        case None => filteredCandidates = Set.empty
      }
    }
    results.toSet
  }

  private def simpleConformanceCheck(c: ScalaResolveResult): Option[Candidate] = {
    c.element match {
      case typeable: Typeable =>
        val subst = c.substitutor
        typeable.`type`() match {
          case Right(t) =>
            if (!subst(t).conforms(tp))
              reportWrong(c, subst, TypeDoesntConformResult)
            else
              Some(c.copy(implicitReason = OkResult), subst)
          case _ =>
            reportWrong(c, subst, BadTypeResult)
        }
      case _ => None
    }
  }

  private def inferValueType(tp: ScType): (ScType, Seq[TypeParameter]) = {
    if (isExtensionConversion) {
      tp match {
        case ScTypePolymorphicType(internalType, typeParams) =>
          val filteredTypeParams =
            typeParams.filter(tp => !tp.lowerType.equiv(Nothing) || !tp.upperType.equiv(Any))
          val newPolymorphicType = ScTypePolymorphicType(internalType, filteredTypeParams)
          val updated = newPolymorphicType.inferValueType.updateLeaves {
            case u: UndefinedType => u.inferValueType
          }
          (updated, typeParams)
        case _ => (tp.inferValueType, Seq.empty)
      }
    } else tp match {
      case ScTypePolymorphicType(_, typeParams) => (tp.inferValueType, typeParams)
      case _ => (tp.inferValueType, Seq.empty)
    }
  }

  private def updateNonValueType(nonValueType0: ScType): ScType = {
    InferUtil.updateAccordingToExpectedType(
      nonValueType0,
      filterTypeParams = isImplicitConversion,
      expectedType = Some(tp),
      place,
      canThrowSCE = true
    )
  }

  private def updateImplicitParameters(
    c:                 ScalaResolveResult,
    nonValueType0:     ScType,
    hasImplicitClause: Boolean,
    depTypeReverter:   ScType => ScType
  ): Option[Candidate] = {
    val fun = c.element.asInstanceOf[ScFunction]
    val subst = c.substitutor

    def wrongTypeParam(result: ImplicitResult): Some[Candidate] = {
      Some(c.copy(problems = Seq(WrongTypeParameterInferred), implicitReason = result), subst)
    }

    def reportParamNotFoundResult(implicitParams: Seq[ScalaResolveResult]): Option[Candidate] = {
      reportWrong(c.copy(implicitParameters = implicitParams), subst, ImplicitParameterNotFoundResult)
    }

    def noImplicitParametersResult(nonValueType: ScType): Some[Candidate] = {
      val (valueType, typeParams) = inferValueType(nonValueType)
      val result = c.copy(
        implicitParameterType = Some(valueType),
        implicitReason = OkResult,
        unresolvedTypeParameters = Some(typeParams)
      )
      Some(result, subst)
    }

    def fullResult(
      resType:          ScType,
      implicitParams:   Seq[ScalaResolveResult],
      checkConformance: Boolean = false
    ): Option[Candidate] = {
      val (valueType, typeParams) = inferValueType(resType)

      val allImportsUsed = implicitParams.map(_.importsUsed).foldLeft(c.importsUsed)(_ ++ _)

      val result = c.copy(
        implicitParameterType = Some(valueType),
        implicitParameters = implicitParams,
        implicitReason = OkResult,
        unresolvedTypeParameters = Some(typeParams),
        importsUsed = allImportsUsed
      )

      if (checkConformance && !valueType.conforms(tp))
        reportWrong(result, subst, TypeDoesntConformResult, Seq(WrongTypeParameterInferred))
      else Option(result, subst)
    }

    def wrongExtensionConversion(nonValueType: ScType): Option[Candidate] = {
      extensionData.flatMap { data =>
        inferValueType(nonValueType) match {
          case (FunctionType(rt, _), _) =>
            val newCandidate = (c.copy(implicitParameterType = Some(rt)), subst)
            if (extensionConversionCheck(data, newCandidate).isEmpty)
              wrongTypeParam(CantFindExtensionMethodResult)
            else None
          //this is not a function, when we still need to pass implicit?..
          case _ =>
            wrongTypeParam(UnhandledResult)
        }
      }
    }

    val (hadDependents, nonValueType) =
      try {
        val updated        = updateNonValueType(nonValueType0)
        val withDependents = depTypeReverter(updated)
        val hadDependents  = withDependents ne updated
        (hadDependents, withDependents)
      }
      catch {
        case _: SafeCheckException => return wrongTypeParam(CantInferTypeParameterResult)
      }

    val depth = ScalaProjectSettings.getInstance(project).getImplicitParametersSearchDepth
    val notTooDeepSearch = depth < 0 || searchImplicitsRecursively < depth

    if (hasImplicitClause && notTooDeepSearch) {

      if (!hadDependents) {
        wrongExtensionConversion(nonValueType) match {
          case Some(errorResult) => return Some(errorResult)
          case None              => ()
        }
      }

      try {
        val (resType, implicitParams0) = InferUtil.updateTypeWithImplicitParameters(nonValueType, place, Some(fun),
          canThrowSCE = !fullInfo, searchImplicitsRecursively + 1, fullInfo)
        val implicitParams = implicitParams0.getOrElse(Seq.empty)

        if (implicitParams.exists(_.isImplicitParameterProblem))
          reportParamNotFoundResult(implicitParams)
        else
          fullResult(resType, implicitParams, hadDependents)
      }
      catch {
        case _: SafeCheckException => wrongTypeParam(CantInferTypeParameterResult)
      }
    } else {
      noImplicitParametersResult(nonValueType)
    }
  }

  def checkFunctionType(
    c:               ScalaResolveResult,
    fun:             ScFunction,
    ret:             ScType,
    depTypeReverter: ScType => ScType = identity
  ): Option[Candidate] = {
    val subst = c.substitutor

    def compute(): Option[Candidate] = {
      val typeParameters = fun.typeParameters
      val implicitClause = fun.effectiveParameterClauses.lastOption.filter(_.isImplicit)
      if (typeParameters.isEmpty && implicitClause.isEmpty) Some(c.copy(implicitReason = OkResult), subst)
      else {
        val methodType = implicitClause.map {
          li => ScMethodType(ret, li.getSmartParameters, isImplicit = true)(place.elementScope)
        }.fold(ret)(subst)

        val polymorphicTypeParameters = typeParameters.map(TypeParameter(_).update(subst))

        val nonValueType0: ScType =
          if (polymorphicTypeParameters.isEmpty) methodType
          else ScTypePolymorphicType(methodType, polymorphicTypeParameters)

        try updateImplicitParameters(c, nonValueType0, implicitClause.isDefined, depTypeReverter)
        catch {
          case _: SafeCheckException =>
            Some(c.copy(problems = Seq(WrongTypeParameterInferred), implicitReason = UnhandledResult), subst)
        }
      }
    }

    if (isImplicitConversion) compute()
    else {
      val coreTypeForTp = coreType(tp)
      val element = coreElement.getOrElse(place)

      def equivOrDominates(tp: ScType, found: ScType): Boolean =
        found.equiv(tp, ConstraintSystem.empty, falseUndef = false).isRight || dominates(tp, found)

      def checkRecursive(tp: ScType, searches: Seq[ScType]): Boolean = searches.exists(equivOrDominates(tp, _))

      def divergedResult = reportWrong(c, c.substitutor, DivergedImplicitResult)

      if (ImplicitsRecursionGuard.isRecursive(element, coreTypeForTp, checkRecursive)) divergedResult
      else {
        ImplicitsRecursionGuard.beforeComputation(element, coreTypeForTp)
        try {
          compute().orElse(divergedResult)
        } finally {
          ImplicitsRecursionGuard.afterComputation(element)
        }
      }
    }
  }

  private def reportWrong(c: ScalaResolveResult, subst: ScSubstitutor, reason: ImplicitResult, problems: Seq[ApplicabilityProblem] = Seq.empty): Option[Candidate] = {
    if (fullInfo) Some(c.copy(problems = problems, implicitReason = reason), subst)
    else None
  }

  private def isPredefConforms(fun: ScFunction) = {
    val name = fun.name
    val clazz = fun.containingClass
    (name == "conforms" || name == "$conforms") && clazz != null && clazz.qualifiedName == "scala.Predef"
  }

  private def hasTypeParamsInType(fun: ScFunction, funType: ScType): Boolean = {
    val cache = ImplicitCollector.cache(project)
    cache.typeParametersOwners(funType).contains(fun)
  }

  private def substedFunType(fun: ScFunction, funType: ScType, subst: ScSubstitutor, withLocalTypeInference: Boolean, noReturnType: Boolean): Option[ScType] = {
    if (!fun.hasTypeParameters) Some(subst(funType))
    else if (noReturnType) {
      val inferredSubst = subst.followed(ScalaPsiUtil.inferMethodTypesArgs(fun, subst))
      Some(inferredSubst(funType))
    }
    else {
      val hasTypeParametersInType: Boolean = hasTypeParamsInType(fun, funType)
      if (withLocalTypeInference && hasTypeParametersInType) {
        val inferredSubst = subst.followed(ScalaPsiUtil.inferMethodTypesArgs(fun, subst))
        Some(inferredSubst(funType))
      } else if (!withLocalTypeInference && !hasTypeParametersInType) {
        Some(subst(funType))
      } else None
    }
  }

  private def checkFunctionByType(
    c:                      ScalaResolveResult,
    withLocalTypeInference: Boolean,
    checkFast:              Boolean,
    noReturnType:           Boolean
  ): Option[Candidate] = {
    val fun = c.element.asInstanceOf[ScFunction]
    val subst = c.substitutor

    val ft =
      if (noReturnType) fun.functionTypeNoImplicits(Some(Nothing))
      else fun.functionTypeNoImplicits()

    ft match {
      case Some(_funType: ScType) =>
        val macroEvaluator = ScalaMacroEvaluator.getInstance(project)
        val funType = macroEvaluator.checkMacro(fun, MacroContext(place, Some(tp))) getOrElse _funType

        val substedFunTp = substedFunType(fun, funType, subst, withLocalTypeInference, noReturnType) match {
          case Some(t) => t
          case None    => return None
        }

        val (withoutDependents, reverter) = approximateDependent(substedFunTp, fun.parameters.toSet)

        if (isExtensionConversion && argsConformWeakly(substedFunTp, tp) || (withoutDependents conforms tp)) {
          if (checkFast || noReturnType) Some(c, ScSubstitutor.empty)
          else checkFunctionType(c, fun, withoutDependents, reverter)
        }
        else if (noReturnType) Some(c, ScSubstitutor.empty)
        else {
          substedFunTp match {
            case FunctionType(ret, params) if params.isEmpty =>
              if (!ret.conforms(tp)) None
              else if (checkFast) Some(c, ScSubstitutor.empty)
              else checkFunctionType(c, fun, ret)
            case _ =>
              reportWrong(c, subst, TypeDoesntConformResult)
          }
        }
      case _ =>
        if (!withLocalTypeInference) reportWrong(c, subst, BadTypeResult)
        else None
    }
  }

  /**
    * Dependency on an implicit argument is like a dependency on type parameter, thus
    * before checking implicit return type conformance we have to substitute parameter-dependent
    * types with `UndefinedType`, otherwise compatibility check is bound to fail.
    * We also have to verify (after we succesfully found some implicit to be compatible)
    * that result type with argument-dependent types restored does indeed conform to `tp`.
    *
    * @param tpe Return type of an implicit currently undergoing a compatibility check
    * @return `tpe` with parameter-dependent types replaced with `UndefinedType`s,
    *         and a mean of reverting this process (useful once type parameters have been inferred
    *         and dependents need to actually be updated according to argument types)
    */
  private[this] def approximateDependent(
    tpe:    ScType,
    params: Set[ScParameter]
  ): (ScType, ScType => ScType) = {
    import org.jetbrains.plugins.scala.lang.psi.api.statements.params._
    import scala.collection.mutable

    val updates = mutable.Map.empty[UndefinedType, ScProjectionType]

    var uid = 0
    val updatedType = tpe.updateRecursively {
      case original @ ScProjectionType(ScDesignatorType(p: ScParameter), _) if params.contains(p) =>
        val typeParam = TypeParameter.light(p.name ++ "$$dep" + uid, Seq.empty, Nothing, Any)
        val undef     = UndefinedType(typeParam)
        updates += (undef -> original)
        uid += 1
        undef
    }

    val reverter: ScType => ScType =
      if (updates.nonEmpty)
        (tpe: ScType) => tpe.updateLeaves {
          case undef: UndefinedType if updates.contains(undef) => updates(undef)
        }
      else identity

    (updatedType, reverter)
  }

  private def applyExtensionPredicate(cand: Candidate): Option[Candidate] = {
    extensionData match {
      case None => Some(cand)
      case Some(data) =>
        extensionConversionCheck(data, cand).orElse {
          val (c, s) = cand
          reportWrong(c, s, CantFindExtensionMethodResult)
        }
    }
  }

  private def abstractsToUpper(tp: ScType): ScType = {
    val noAbstracts = tp.updateLeaves {
      case ScAbstractType(_, _, upper) => upper
    }

    noAbstracts.removeAliasDefinitions()
  }

  private def coreType(tp: ScType): ScType = {
    tp match {
      case ScCompoundType(comps, _, _) => abstractsToUpper(ScCompoundType(comps, Map.empty, Map.empty)).removeUndefines()
      case ScExistentialType(quant, _) => abstractsToUpper(ScExistentialType(quant.recursiveUpdate {
        case arg: ScExistentialArgument => ReplaceWith(arg.upper)
        case _ => ProcessSubtypes
      })).removeUndefines()
      case _ => abstractsToUpper(tp).removeUndefines()
    }
  }

  private def dominates(t: ScType, u: ScType): Boolean = {
    complexity(t) > complexity(u) && topLevelTypeConstructors(t).intersect(topLevelTypeConstructors(u)).nonEmpty
  }

  private def topLevelTypeConstructors(tp: ScType): Set[ScType] = {
    tp match {
      case ScProjectionType(_, element) => Set(ScDesignatorType(element))
      case ParameterizedType(designator, _) => Set(designator)
      case tp@ScDesignatorType(_: ScObject) => Set(tp)
      case ScDesignatorType(v: ScTypedDefinition) =>
        val valueType: ScType = v.`type`().getOrAny
        topLevelTypeConstructors(valueType)
      case ScCompoundType(comps, _, _) => comps.flatMap(topLevelTypeConstructors).toSet
      case _ => Set(tp)
    }
  }

  private def complexity(tp: ScType): Int = {
    tp match {
      case ScProjectionType(proj, _) => 1 + complexity(proj)
      case ParameterizedType(_, args) => 1 + args.foldLeft(0)(_ + complexity(_))
      case ScDesignatorType(_: ScObject) => 1
      case ScDesignatorType(v: ScTypedDefinition) =>
        val valueType: ScType = v.`type`().getOrAny
        1 + complexity(valueType)
      case ScCompoundType(comps, _, _) => comps.foldLeft(0)(_ + complexity(_))
      case _ => 1
    }
  }

  private def argsConformWeakly(left: ScType, right: ScType): Boolean = {
    def function1Arg(scType: ScType): Option[ScType] = scType match {
      case ParameterizedType(ScDesignatorType(c: PsiClass), args) if args.size == 2 =>
        if (c.qualifiedName == "scala.Function1") args.headOption
        else None
      case _ => None
    }

    function1Arg(left) match {
      case Some(leftArg) => function1Arg(right) match {
        case Some(rightArg) => rightArg.weakConforms(leftArg)
        case _ => false
      }
      case _ => false
    }
  }
}
