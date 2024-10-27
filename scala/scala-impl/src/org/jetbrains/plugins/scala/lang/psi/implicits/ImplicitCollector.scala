package org.jetbrains.plugins.scala.lang.psi.implicits

import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.project.Project
import com.intellij.psi._
import com.intellij.util.SlowOperations
import org.jetbrains.plugins.scala.caches.measure
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.macros.evaluator.{MacroContext, ScalaMacroEvaluator}
import org.jetbrains.plugins.scala.lang.psi.api.InferUtil
import org.jetbrains.plugins.scala.lang.psi.api.InferUtil.SafeCheckException
import org.jetbrains.plugins.scala.lang.psi.api.statements._
import org.jetbrains.plugins.scala.lang.psi.api.statements.params.{ScParameter, TypeParamIdOwner}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.{ScClass, ScObject, ScTemplateDefinition, ScTypeDefinition}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.{ScNamedElement, ScTypedDefinition}
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiManager
import org.jetbrains.plugins.scala.lang.psi.implicits.ExtensionConversionHelper.extensionConversionCheck
import org.jetbrains.plugins.scala.lang.psi.implicits.ImplicitCollector._
import org.jetbrains.plugins.scala.lang.psi.light.LightContextFunctionParameter
import org.jetbrains.plugins.scala.lang.psi.types._
import org.jetbrains.plugins.scala.lang.psi.types.api._
import org.jetbrains.plugins.scala.lang.psi.types.api.designator._
import org.jetbrains.plugins.scala.lang.psi.types.nonvalue.ScTypePolymorphicType
import org.jetbrains.plugins.scala.lang.psi.types.recursiveUpdate.AfterUpdate.{ProcessSubtypes, ReplaceWith}
import org.jetbrains.plugins.scala.lang.psi.types.recursiveUpdate.ScSubstitutor
import org.jetbrains.plugins.scala.lang.psi.types.result._
import org.jetbrains.plugins.scala.lang.resolve.ScalaResolveState.ResolveStateExt
import org.jetbrains.plugins.scala.lang.resolve._
import org.jetbrains.plugins.scala.lang.resolve.processor.MostSpecificUtil
import org.jetbrains.plugins.scala.project.ProjectContext
import org.jetbrains.plugins.scala.settings.ScalaProjectSettings

import scala.collection.mutable
import scala.util.Using

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

    def presentableTypeText: String =
      Using.resource(SlowOperations.knownIssue("SCL-23054"))(_ => tp.presentableText(place))
  }

  def probableArgumentsFor(parameter: ScalaResolveResult): Seq[(ScalaResolveResult, FullInfoResult)] = {
    parameter.implicitSearchState.map { state =>
      val collector = new ImplicitCollector(state.copy(fullInfo = true))
      collector.collect().flatMap { r =>
        r.implicitReason match {
          case CantInferTypeParameterResult => Seq.empty
          case reason: FullInfoResult => Seq((r, reason))
          case _ => Seq.empty
        }
      }
    }.getOrElse {
      Seq.empty
    }
  }

  def visibleImplicits(place: PsiElement): Set[ScalaResolveResult] =
    ImplicitSearchScope.forElement(place).cachedVisibleImplicits

  def implicitsFromType(
    place:                  PsiElement,
    scType:                 ScType,
  ): Set[ScalaResolveResult] =
    ImplicitSearchScope.forElement(place).cachedImplicitsByType(scType)

  def isValidImplicitResult(srr: ScalaResolveResult): Boolean =
    !srr.problems.contains(WrongTypeParameterInferred) && srr.implicitReason != TypeDoesntConformResult
}

/**
 * @param place The call site
 * @param tp    Search for an implicit definition of this type. May have type variables.
 * @param forDeferredGivenInClass Template definition for which deferred given instance search was initiated
 */
class ImplicitCollector(
  place:                      PsiElement,
  tp:                         ScType,
  expandedTp:                 ScType,
  coreElement:                Option[ScNamedElement],
  isImplicitConversion:       Boolean,
  searchImplicitsRecursively: Int                                          = 0,
  extensionData:              Option[ExtensionConversionData]              = None,
  fullInfo:                   Boolean                                      = false,
  previousRecursionState:     Option[ImplicitsRecursionGuard.RecursionMap] = None,
  withExtensions:             Boolean                                      = false,
  forCompletion:              Boolean                                      = false,
  forDeferredGivenInClass:    Option[ScTemplateDefinition]                 = None
) {
  def this(state: ImplicitState) = {
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

  private def canContainTargetMethod(srr: ScalaResolveResult): Boolean = measure("ImplicitCollector.canContainTargetMethod") {
    withExtensions && !srr.isExtensionCall && !hasExplicitClause(srr) && {
      val targetType = srr.element match {
        case param: ScParameter => param.typeElement.flatMap(_.`type`().toOption)
        case fun: ScFunction    => fun.returnType.toOption
        case _                  => None
      }

      val hasTargetMethod =
        for {
          data       <- extensionData
          targetName = data.refName
          rtpe       <- targetType
          cls        <- rtpe.extractClass
          tdef       <- cls.asOptionOf[ScTypeDefinition]
        } yield tdef.methodsByName(targetName).nonEmpty

      hasTargetMethod.getOrElse(true)
    }
  }

  def collect(): Seq[ScalaResolveResult] = {
    def calc(): Seq[ScalaResolveResult] = {
      clazz match {
        case Some(c) if InferUtil.tagsAndManifists.contains(c.qualifiedName) => return Seq.empty
        case _                                                               =>
      }

      ProgressManager.checkCanceled()
      if (fullInfo) {
        val visible = visibleNamesCandidates()
        val fromNameCandidates = collectFullInfo(visible)

        val allCandidates =
          if (fromNameCandidates.exists(_.implicitReason == OkResult)) fromNameCandidates
          else {
            val fromTypeNotVisible =
              fromTypeCandidates()
                .filterNot(c => visible.exists(_.element == c.element))

            fromNameCandidates ++ collectFullInfo(fromTypeNotVisible)
          }

        //todo: should we also compare types like in MostSpecificUtil.isAsSpecificAs ?
        allCandidates.sortWith(mostSpecificUtil.isInMoreSpecificClass)
      }
      else if (forCompletion) {
        val allCandidates = visibleNamesCandidates() ++ fromTypeCandidates()
        collectCompatibleForCompletion(allCandidates)
      }
      else {
        ImplicitCollector.cache(project)
          .getOrCompute(place, tp, mayCacheResult = !isExtensionConversion) {
            //Step 1: Process only extension candidates in lexical scope
            //Step 2: Try implicits/givens from lexical scope and extensions inside given definitions
            //Step 3: Try implicits/givens/extension from implicit scope and extension inside given definitions
            val additionalClassParameters =
              forDeferredGivenInClass.collect {
                case cls: ScClass =>
                  cls.parameters.collect {
                    case p if p.isImplicitOrContextParameter => new ScalaResolveResult(p)
                  }
              }.getOrElse(Seq.empty)

            val visible = visibleNamesCandidates() ++ additionalClassParameters

            val (visibleExtensions, otherVisibleCandidates) = visible.partition(_.isExtensionCall)

            val extensionCandidates =
              if (withExtensions) compatible(visibleExtensions)
              else                Seq.empty

            if (extensionCandidates.exists(_.isApplicable())) extensionCandidates
            else {
              val firstCandidates = compatible(otherVisibleCandidates)
              if (firstCandidates.exists(_.isApplicable())) firstCandidates
              else {
                val secondCandidates = compatible(fromTypeCandidates())
                if (secondCandidates.nonEmpty) secondCandidates else firstCandidates
              }
            }
          }
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

  private def visibleNamesCandidates(): Set[ScalaResolveResult] =
    ImplicitCollector.visibleImplicits(place)
      .map(_.copy(implicitSearchState = Some(collectorState)))

  private def fromTypeCandidates(): Set[ScalaResolveResult] =
    ImplicitCollector.implicitsFromType(place, expandedTp)
      .map(_.copy(implicitSearchState = Some(collectorState)))

  private def compatible(candidates: Set[ScalaResolveResult]): Seq[ScalaResolveResult] = {
    //implicits found without local type inference have higher priority
    val withoutLocalTypeInference = collectCompatibleCandidates(candidates, withLocalTypeInference = false)

    val compatible =
      if (withoutLocalTypeInference.nonEmpty) withoutLocalTypeInference
      else                                    collectCompatibleCandidates(candidates, withLocalTypeInference = true)

    if (compatible.forall(_.isExtensionCall)) compatible.toSeq
    else
      mostSpecificUtil.mostSpecificForImplicitParameters(compatible) match {
        case Some(r) => Seq(r)
        case _       => compatible.toSeq
      }
  }

  private def collectFullInfo(candidates: Set[ScalaResolveResult]): Seq[ScalaResolveResult] = {
    val allCandidates =
      candidates.flatMap(c => checkCompatible(c, withLocalTypeInference = false)) ++
        candidates.flatMap(c => checkCompatible(c, withLocalTypeInference = true))
    val afterExtensionPredicate = allCandidates.flatMap(applyExtensionPredicate)

    afterExtensionPredicate
      .filter(_.implicitReason.is[FullInfoResult])
      .toSeq
  }

  private def collectCompatibleForCompletion(candidates: Set[ScalaResolveResult]): Seq[ScalaResolveResult] = {
    val filteredCandidates = mutable.HashSet.empty[ScalaResolveResult]

    for (c <- candidates) {
      val compatible = checkCompatible(c, withLocalTypeInference = false) ++ checkCompatible(c, withLocalTypeInference = true)
      filteredCandidates ++= compatible.filter(isValidImplicitResult)
      if (withExtensions) {
        filteredCandidates ++= collectExtensionsFromImplicitResult(c, extensionData, c.substitutor)
      }
    }
    filteredCandidates.toSeq
  }

  private def possibleFunctionN(clazz: PsiClass): Option[Int] =
    clazz.qualifiedName match {
      case "java.lang.Object" => Some(-1)
      case name =>
        val paramsNumber = name.stripPrefix(FunctionType.TypeName)
        if (paramsNumber.nonEmpty && paramsNumber.forall(_.isDigit)) Some(paramsNumber.toInt)
        else None
    }

  def checkCompatible(
    c:                      ScalaResolveResult,
    withLocalTypeInference: Boolean,
    checkFast:              Boolean = false
  ): Option[ScalaResolveResult] = {
    ProgressManager.checkCanceled()

    c.element match {
      case fun: ScFunction =>
        val exportedInExtension = c.exportedInExtension

        if (fun.typeParametersWithExtension(exportedInExtension).isEmpty && withLocalTypeInference) return None

        //scala.Predef.$conforms should be excluded
        if (isImplicitConversion && isPredefConforms(fun)) return None

        val clauses = fun.effectiveParameterClauses
        //to avoid checking implicit functions in case of simple implicit parameter search
        val hasNonImplicitClause = clauses.exists(!_.isImplicitOrUsing)
        if (!c.isExtensionCall && hasNonImplicitClause) {
          val clause = clauses.head
          val paramsCount = clause.parameters.size
          if (!possibleScalaFunction.exists(x => x == -1 || x == paramsCount)) {
            return reportWrong(c, FunctionForParameterResult, Seq(WrongTypeParameterInferred))
          }
        }

        checkFunctionByType(c, withLocalTypeInference, checkFast)
      case _ =>
        if (withLocalTypeInference) {
          if (withExtensions) Option(c.copy(implicitReason = TypeDoesntConformResult))
          else                None
        } //only functions may have local type inference
        else simpleConformanceCheck(c)
    }
  }

  def collectCompatibleCandidates(
    candidates:             Set[ScalaResolveResult],
    withLocalTypeInference: Boolean,
  ): Set[ScalaResolveResult] = {
    val filteredCandidates = mutable.HashSet.empty[ScalaResolveResult]

    val iterator = candidates.iterator
    while (iterator.hasNext) {
      val c = iterator.next()

      if (canContainTargetMethod(c)) {
        //no point in filtering candidates by type if they are potentially holding
        //extensions, that we are looking for
        filteredCandidates += c
      } else filteredCandidates ++= checkCompatible(c, withLocalTypeInference, checkFast = true)
    }

    var results = Set.empty[ScalaResolveResult]

    while (filteredCandidates.nonEmpty) {
      val next = mostSpecificUtil.nextMostSpecific(filteredCandidates)
      next match {
        case Some(c) =>
          filteredCandidates.remove(c)

          val compatible = checkCompatible(c, withLocalTypeInference)

          if (withExtensions) {
            //process return types of all candidates to search for extensions
            for {
              result <- compatible
            } {
              val subst      = result.substitutor
              val extensions = collectExtensionsFromImplicitResult(result, extensionData, subst)
              filteredCandidates ++= extensions
            }
          }

          //filter away candidates, which only got through compatibility check,
          //because they might contain extensions
          val afterExtensionPredicate = compatible.filter(isValidImplicitResult).flatMap(applyExtensionPredicate)

          afterExtensionPredicate match {
            case Some(r) =>
              val notMoreSpecific = mostSpecificUtil.notMoreSpecificThan(r)
              if (!c.isExtensionCall) {
                filteredCandidates.filterInPlace(notMoreSpecific)
                //this filter was added to make result deterministic
                results = results.filter(c => notMoreSpecific(c))
              }
              results = results + r
            case _ =>
          }
        case None => ()
      }
    }

    results
  }

  /**
   * Apart from being located directly in the lexical or implicit scope, extensions
   * can also be located inside implicit/given definitions inside the aforementioned scopes.
   */
  private def collectExtensionsFromImplicitResult(
    result:        ScalaResolveResult,
    extensionData: Option[ExtensionConversionData],
    subst:         ScSubstitutor
  ): Set[ScalaResolveResult] = {
    val proc  = new ExtensionProcessor(place, name = extensionData.map(_.refName).getOrElse(""), forCompletion)
    val tp    = InferUtil.extractImplicitParameterType(result)

    val unresolvedTypeParams = result.unresolvedTypeParameters

    tp.foreach { t =>
      val state = ScalaResolveState
        .withImplicitScopeObject(t)
        .withImportsUsed(result.importsUsed)

      val stateWithUnresolved = unresolvedTypeParams match {
        case Some(params) => state.withUnresolvedTypeParams(params)
        case None         => state
      }

      proc.processType(t, place, stateWithUnresolved)
    }

    proc.candidatesS
  }

  //@TODO: apply context function to implicit args if type of `c` does not conform
  //       to expected type
  private def simpleConformanceCheck(c: ScalaResolveResult): Option[ScalaResolveResult] = {
    c.element match {
      case typeable: Typeable =>
        val subst = c.substitutor
        typeable.`type`() match {
          case Right(t) =>
            val conformance = subst(t).conformsIn(place, tp, ConstraintSystem.empty)
            conformance match {
              case ConstraintSystem(subst) =>
                //Update synthetic parameters, coming from expected context-function type
                typeable match {
                  case contextParam: LightContextFunctionParameter if !isImplicitConversion =>
                    contextParam.updateWithSubst(subst)
                  case _ => ()
                }

                Option(c.copy(implicitReason = OkResult))
              case _ =>
                reportWrong(c, TypeDoesntConformResult, propagateFailures = withExtensions)
            }
          case _ => reportWrong(c, BadTypeResult, propagateFailures = withExtensions)
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
      expectedType     = Some(tp),
      place,
      canThrowSCE = true
    )
  }

  private def updateImplicitParameters(
    c:                       ScalaResolveResult,
    nonValueType0:           ScType,
    hasImplicitClause:       Boolean,
    hadDependents:           Boolean,
    expectedTypeConstraints: ConstraintSystem
  ): Option[ScalaResolveResult] = {
    val fun            = c.element.asInstanceOf[ScFunction]
    val canContainExts = canContainTargetMethod(c)

    def wrongTypeParam(nonValueType: ScType, result: ImplicitResult): Some[ScalaResolveResult] = {
      val (valueType, typeParams) = inferValueType(nonValueType)
      Some(c.copy(
        problems = Seq(WrongTypeParameterInferred),
        implicitParameterType = Some(valueType),
        implicitReason = result,
        unresolvedTypeParameters = Some(typeParams)
      ))
    }

    def reportParamNotFoundResult(resType: ScType, implicitParams: Seq[ScalaResolveResult]): Option[ScalaResolveResult] = {
      val (valueType, typeParams) = inferValueType(resType)
      reportWrong(
        c.copy(
          implicitParameters       = implicitParams,
          implicitParameterType    = Some(valueType),
          unresolvedTypeParameters = Some(typeParams)
        ),
        ImplicitParameterNotFoundResult
      )
    }

    def noImplicitParametersResult(nonValueType: ScType): Some[ScalaResolveResult] = {
      val (valueType, typeParams) = inferValueType(nonValueType)

      val subst = expectedTypeConstraints match {
        case ConstraintSystem(subst) => subst
        case _                       => ScSubstitutor.empty
      }

      val result = c.copy(
        subst                    = c.substitutor.followed(subst),
        implicitParameterType    = Some(valueType),
        implicitReason           = OkResult,
        unresolvedTypeParameters = Some(typeParams)
      )
      Some(result)
    }

    def fullResult(
      resType:          ScType,
      implicitParams:   Seq[ScalaResolveResult],
      constraints:      ConstraintSystem,
      checkConformance: Boolean = false
    ): Option[ScalaResolveResult] = {
      val (valueType, typeParams) = inferValueType(resType)
      val allConstraints = constraints + expectedTypeConstraints

      val constraintSubst = allConstraints match {
        case ConstraintSystem(subst) => Option(subst)
        case _                       => None
      }

      constraintSubst.fold(reportWrong(c, CantInferTypeParameterResult)) { subst =>
        val allImportsUsed = implicitParams.map(_.importsUsed).foldLeft(c.importsUsed)(_ ++ _)

        val result = c.copy(
          subst                    = c.substitutor.followed(subst),
          implicitParameterType    = Option(valueType),
          implicitParameters       = implicitParams,
          implicitReason           = OkResult,
          unresolvedTypeParameters = Option(typeParams),
          importsUsed              = allImportsUsed
        )

        if (checkConformance && !valueType.conforms(tp))
          reportWrong(result, TypeDoesntConformResult, Seq(WrongTypeParameterInferred))
        else Option(result)
      }
    }

    def wrongExtensionConversion(nonValueType: ScType): Option[ScalaResolveResult] = {
      extensionData.flatMap { data =>
        inferValueType(nonValueType) match {
          case (FunctionType(rt, _), _) =>
            val newCandidate = c.copy(implicitParameterType = Some(rt))
            if (extensionConversionCheck(data, newCandidate).isEmpty)
              wrongTypeParam(nonValueType, CantFindExtensionMethodResult)
            else None
          //this is not a function, when we still need to pass implicit?..
          case _ => wrongTypeParam(nonValueType, UnhandledResult)
        }
      }
    }

    val (nonValueType, failedPtAdapt) =
      try {
        val updated = updateNonValueType(nonValueType0)

        val noDependents =
          if (hadDependents) UndefinedType.revertDependentTypes(updated)
          else               updated

        val propagatedError = Option.when(c.implicitReason != NoResult){
          val (_, unresolvedTps) = inferValueType(nonValueType0)

          if (unresolvedTps.isEmpty) c
          else                       c.copy(unresolvedTypeParameters = Option(unresolvedTps))
        }

        (noDependents, propagatedError)
      }
      catch {
        case _: SafeCheckException =>
          val result = wrongTypeParam(nonValueType0, CantInferTypeParameterResult)

          if (canContainExts) (nonValueType0, result)
          else                return result
      }

    val depth = ScalaProjectSettings.getInstance(project).getImplicitParametersSearchDepth
    val notTooDeepSearch = depth < 0 || searchImplicitsRecursively < depth

    if (hasImplicitClause && notTooDeepSearch && !c.isExtensionCall) {

      val conversionDataCheckedResult =
        if (!hadDependents) {
          val noMethod = wrongExtensionConversion(nonValueType)
          failedPtAdapt.orElse(noMethod)
        } else failedPtAdapt

      if (!canContainExts) {
        conversionDataCheckedResult.foreach(result => return Option(result))
      }

      try {
        val (resType, implicitParams0, constraints) =
          InferUtil.updateTypeWithImplicitParameters(
            nonValueType,
            place,
            Some(fun),
            canThrowSCE = !fullInfo,
            searchImplicitsRecursively + 1,
            fullInfo
          )

        val implicitParams = implicitParams0.getOrElse(Seq.empty)

        if (implicitParams.exists(_.isImplicitParameterProblem))
          reportParamNotFoundResult(resType, implicitParams)
        else
          conversionDataCheckedResult match {
            case Some(earlierError) =>
              constraints.toSubst.fold(earlierError)(constraintSubst =>
                earlierError.copy(subst = earlierError.substitutor.followed(constraintSubst))
              ).toOption
            case _ => fullResult(resType, implicitParams, constraints, hadDependents)
          }
      } catch {
        case _: SafeCheckException => wrongTypeParam(nonValueType, CantInferTypeParameterResult)
      }
    } else {
      failedPtAdapt.orElse(
        noImplicitParametersResult(nonValueType)
      )
    }
  }

  private def checkFunctionType(
    c:                ScalaResolveResult,
    nonValueFunTypes: NonValueFunctionTypes,
    constraints:      ConstraintSystem
  ): Option[ScalaResolveResult] = measure("ImplicitCollector.checkFunctionType") {
    def compute(): Option[ScalaResolveResult] = {
      nonValueFunTypes.methodType match {
        case None =>
          if (c.implicitReason != NoResult) Option(c)
          else                              Option(c.copy(implicitReason = OkResult))

        case Some(nonValueType0) =>
          try {
            updateImplicitParameters(
              c,
              c.substitutor(nonValueType0),
              nonValueFunTypes.hasImplicitClause,
              nonValueFunTypes.hadDependents,
              constraints
            )
          }
          catch {
            case _: SafeCheckException =>
              Some(c.copy(problems = Seq(WrongTypeParameterInferred), implicitReason = UnhandledResult))
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

      def divergedResult = reportWrong(c, DivergedImplicitResult)

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

  private def reportWrong(
    c:                 ScalaResolveResult,
    reason:            ImplicitResult,
    problems:          Seq[ApplicabilityProblem] = Seq.empty,
    propagateFailures: Boolean                   = false
  ): Option[ScalaResolveResult] =
    if (fullInfo || propagateFailures) Option(c.copy(problems = problems, implicitReason = reason))
    else                               None

  private def isPredefConforms(fun: ScFunction) = {
    val name = fun.name
    val clazz = fun.containingClass
    (name == "conforms" || name == "$conforms") && clazz != null && clazz.qualifiedName == "scala.Predef"
  }

  def checkFunctionByType(
    c:                      ScalaResolveResult,
    withLocalTypeInference: Boolean,
    checkFast:              Boolean,
  ): Option[ScalaResolveResult] = measure("ImplicitCollector.checkFunctionByType") {
    val fun                 = c.element.asInstanceOf[ScFunction]
    val exportedInExtension = c.exportedInExtension

    if (fun.typeParametersWithExtension(exportedInExtension).nonEmpty && !withLocalTypeInference)
      return None

    val macroEvaluator = ScalaMacroEvaluator.getInstance(project)
    val typeFromMacro  = macroEvaluator.checkMacro(fun, MacroContext(place, Some(tp)))

    val undefineGivenInstanceParameters =
      if (c.isExtensionCall) {
        val typeParams = c.unresolvedTypeParameters.getOrElse(Seq.empty)
        ScSubstitutor.bind(typeParams)(UndefinedType(_))
      }
      else ScSubstitutor.empty

    val nonValueFunctionTypes =
      ImplicitCollector.cache(project).getNonValueTypes(
        fun,
        c.substitutor.followed(undefineGivenInstanceParameters),
        exportedInExtension,
        typeFromMacro
      )

    nonValueFunctionTypes.undefinedType match {
      case Some(undefined0: ScType) =>

        val undefined = undefined0 match {
          case Scala3Conversion(argType, resType) if isImplicitConversion => FunctionType(resType, Seq(argType))(fun.elementScope)
          case _ => undefined0
        }

        val undefinedConforms =
          if (isImplicitConversion) checkWeakConformance(place, undefined, maskTypeParametersInExtensions(tp, c))
          else                      undefined.conformsIn(place, tp, ConstraintSystem.empty)

        if (undefinedConforms.isRight) {
          if (checkFast) Option(c)
          else           checkFunctionType(c, nonValueFunctionTypes, undefinedConforms.constraints)
        } else if (canContainTargetMethod(c)) {
          //With the addition of extensions in Scala 3,
          //we now cannot discard implicits based by their type right away,
          //because they might contain extensions, defined on their "return type".
          //So here and further down the function call tree we will not abort on
          //non-fatal failures (everything except for not-found-implicit-parameters problems)
          //and instead propagate them to the very end.
          checkFunctionType(
            c.copy(implicitReason = TypeDoesntConformResult),
            nonValueFunctionTypes,
            undefinedConforms.constraints
          )
        } else reportWrong(c, TypeDoesntConformResult)
      case _ =>
        if (!withLocalTypeInference) reportWrong(c, BadTypeResult)
        else                         None
    }
  }

  /**
   * This is a workaroud to avoid accidental type parameter
   * capturing, when resolving an extension from inside itself, e.g.
   * {{{
   *   extension [A, B] (fa: F[A]) {
   *     def foo(b: B): A = ???
   *     def bar(fab: F[A => B]) = fab.foo
   *   }
   * }}}
   * Here `fab.foo` is problematic, bc unresolved type parameter `B`
   * is propagated to the `foo` method and later is replaced with undefined type,
   * but since `A` is set to `A => B` and all these [[TypeParameterType]]s point to the same
   * physical type parameters `B` in `A => B` is replaced with undefined type as well.
   * To avoid that, here each type parameter ref is replaced with a fresh one
   * lower & upper bounded by the old one (`B` -> `NewB >: B <: B`)
   *
   */
  private def maskTypeParametersInExtensions(tp: ScType, cand: ScalaResolveResult): ScType = {
    val extension = cand.element match {
      case m: ScFunction => m.extensionMethodOwner
      case _             => None
    }

    extension match {
      case Some(ext) =>
        val tpIds = ext.typeParameters.map(_.typeParamId)

        tp.updateRecursively {
          case tpt: TypeParameterType if tpIds.contains(tpt.psiTypeParameter.typeParamId) =>
            val newTp = TypeParameter.light(tpt.name, tpt.typeParameters, tpt, tpt)
            TypeParameterType(newTp)
        }
      case None => tp
    }
  }

  private def applyExtensionPredicate(cand: ScalaResolveResult): Option[ScalaResolveResult] = {
    extensionData match {
      case None => Some(cand)
      case Some(data) =>
        val applicabilityCheck =
          if (cand.isExtensionCall) {
            val candName = cand.renamed.getOrElse(cand.name)
            Option.when(data.refName == candName)(cand)
          } else extensionConversionCheck(data, cand)

        applicabilityCheck.orElse(
          reportWrong(cand, CantFindExtensionMethodResult)
        )
    }
  }

  private def hasExplicitClause(srr: ScalaResolveResult): Boolean = srr.element match {
    case fun: ScFunction =>
      val exportedInExtension = srr.exportedInExtension
      fun.parameterClausesWithExtension(exportedInExtension).exists(!_.isImplicitOrUsing)
    case _ => false
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

  private def complexity(tp: ScType): Int =
    tp match {
      case ScProjectionType(proj, _)     => 1 + complexity(proj)
      case ParameterizedType(_, args)    => 1 + args.foldLeft(0)(_ + complexity(_))
      case ScDesignatorType(_: ScObject) => 1
      case ScDesignatorType(v: ScTypedDefinition) =>
        val valueType: ScType = v.`type`().getOrAny
        1 + complexity(valueType)
      case ScCompoundType(comps, _, _) => comps.foldLeft(0)(_ + complexity(_))
      case _                           => 1
    }

  private def checkWeakConformance(place: PsiElement, left: ScType, right: ScType): ConstraintsResult = {
    import SmartSuperTypeUtil.{TraverseSupers, traverseSuperTypes}

    def function1Arg(scType: ScType, strict: Boolean = true): Option[(ScType, ScType)] = {
      def isFunction1Class(cls: PsiClass): Boolean =
        cls.qualifiedName == "scala.Function1"

      scType match {
        case ParameterizedType(ScDesignatorType(c: PsiClass), args)
          if args.size == 2 && isFunction1Class(c) => (args.head, args.last).toOption
        case _ =>
          if (strict) None
          else {
            var res: Option[(ScType, ScType)] = None

            traverseSuperTypes(
              scType,
              (tpe, cls, _) => (tpe, cls) match {
                case (ParameterizedType(_, args), cls)
                  if args.size == 2 && isFunction1Class(cls) =>
                  res = (args.head, args.last).toOption
                  TraverseSupers.Stop
                case _ => TraverseSupers.ProcessParents
              }
            )

            res
          }

      }
    }

    function1Arg(left, strict = false) match {
      case Some((leftArg, leftRes)) =>
        function1Arg(right) match {
          case Some((rightArg, rightRes)) =>
            rightArg.conformsIn(place, leftArg, ConstraintSystem.empty, checkWeak = true) match {
              case cs: ConstraintSystem => leftRes.conformsIn(place, rightRes, cs)
              case left => left
            }
          case _ => ConstraintsResult.Left
        }
      case _ => ConstraintsResult.Left
    }
  }
}
