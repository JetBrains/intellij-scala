package org.jetbrains.plugins.scala
package lang.psi.implicits

import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.project.Project
import com.intellij.psi._
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.macros.evaluator.{MacroContext, ScalaMacroEvaluator}
import org.jetbrains.plugins.scala.lang.psi.api.InferUtil
import org.jetbrains.plugins.scala.lang.psi.api.InferUtil.SafeCheckException
import org.jetbrains.plugins.scala.lang.psi.api.statements._
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScObject
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.{ScNamedElement, ScTypedDefinition}
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiManager
import org.jetbrains.plugins.scala.lang.psi.implicits.ExtensionConversionHelper.extensionConversionCheck
import org.jetbrains.plugins.scala.lang.psi.implicits.ImplicitCollector._
import org.jetbrains.plugins.scala.lang.psi.types._
import org.jetbrains.plugins.scala.lang.psi.types.api._
import org.jetbrains.plugins.scala.lang.psi.types.api.designator._
import org.jetbrains.plugins.scala.lang.psi.types.nonvalue.ScTypePolymorphicType
import org.jetbrains.plugins.scala.lang.psi.types.recursiveUpdate.AfterUpdate.{ProcessSubtypes, ReplaceWith}
import org.jetbrains.plugins.scala.lang.psi.types.result._
import org.jetbrains.plugins.scala.lang.resolve._
import org.jetbrains.plugins.scala.lang.resolve.processor.MostSpecificUtil
import org.jetbrains.plugins.scala.macroAnnotations.Measure
import org.jetbrains.plugins.scala.project.ProjectContext
import org.jetbrains.plugins.scala.settings.ScalaProjectSettings

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

  def probableArgumentsFor(parameter: ScalaResolveResult): Seq[(ScalaResolveResult, FullInfoResult)] = {
    parameter.implicitSearchState.map { state =>
      val collector = new ImplicitCollector(state.copy(fullInfo = true))
      collector.collect().flatMap { r =>
        r.implicitReason match {
          case reason: FullInfoResult => Seq((r, reason))
          case _ => Seq.empty
        }
      }
    } getOrElse {
      Seq.empty
    }
  }

  private def visibleNamesCandidates(project: Project, place: PsiElement, state: ImplicitState): Set[ScalaResolveResult] =
    ImplicitCollector.cache(project)
      .getVisibleImplicits(place)
      .map(_.copy(implicitSearchState = Some(state)))

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
        ImplicitCollector.cache(project)
          .getOrCompute(place, tp, mayCacheResult = !isExtensionConversion) {
            val firstCandidates = compatible(visibleNamesCandidates)
            if (firstCandidates.exists(_.isApplicable())) firstCandidates
            else {
              val secondCandidates = compatible(fromTypeCandidates)
              if (secondCandidates.nonEmpty) secondCandidates else firstCandidates
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

  private def visibleNamesCandidates: Set[ScalaResolveResult] =
    ImplicitCollector.visibleNamesCandidates(project, place, collectorState)

  private def fromTypeCandidates =
    new ImplicitParametersProcessor(place, withoutPrecedence = true)
      .candidatesByType(expandedTp)
      .map(_.copy(implicitSearchState = Some(collectorState)))

  private def compatible(candidates: Set[ScalaResolveResult]): Seq[ScalaResolveResult] = {
    //implicits found without local type inference have higher priority
    val withoutLocalTypeInference = collectCompatibleCandidates(candidates, withLocalTypeInference = false)

    val compatible =
      if (withoutLocalTypeInference.nonEmpty) withoutLocalTypeInference
      else collectCompatibleCandidates(candidates, withLocalTypeInference = true)


    mostSpecificUtil.mostSpecificForImplicitParameters(compatible) match {
      case Some(r) => Seq(r)
      case _ => compatible.toSeq
    }
  }

  private def collectFullInfo(candidates: Set[ScalaResolveResult]): Seq[ScalaResolveResult] = {
    val allCandidates =
      candidates.flatMap(c => checkCompatible(c, withLocalTypeInference = false)) ++
        candidates.flatMap(c => checkCompatible(c, withLocalTypeInference = true))
    val afterExtensionPredicate = allCandidates.flatMap(applyExtensionPredicate)

    afterExtensionPredicate
      .filter(_.implicitReason.isInstanceOf[FullInfoResult])
      .toSeq
  }

  private def possibleFunctionN(clazz: PsiClass): Option[Int] =
    clazz.qualifiedName match {
      case "java.lang.Object" => Some(-1)
      case name =>
        val paramsNumber = name.stripPrefix(FunctionType.TypeName)
        if (paramsNumber.nonEmpty && paramsNumber.forall(_.isDigit)) Some(paramsNumber.toInt)
        else None
    }

  def checkCompatible(c: ScalaResolveResult, withLocalTypeInference: Boolean, checkFast: Boolean = false): Option[ScalaResolveResult] = {
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
            return reportWrong(c, FunctionForParameterResult, Seq(WrongTypeParameterInferred))
          }
        }

        checkFunctionByType(c, withLocalTypeInference, checkFast)

      case _ =>
        if (withLocalTypeInference) None //only functions may have local type inference
        else simpleConformanceCheck(c)
    }
  }

  def collectCompatibleCandidates(candidates: Set[ScalaResolveResult], withLocalTypeInference: Boolean): Set[ScalaResolveResult] = {
    var filteredCandidates = Set.empty[ScalaResolveResult]

    val iterator = candidates.iterator
    while (iterator.hasNext) {
      val c = iterator.next()
      filteredCandidates ++= checkCompatible(c, withLocalTypeInference, checkFast = true)
    }

    var results: Set[ScalaResolveResult] = Set()

    while (filteredCandidates.nonEmpty) {
      val next = mostSpecificUtil.nextMostSpecific(filteredCandidates)
      next match {
        case Some(c) =>
          filteredCandidates = filteredCandidates - c
          val compatible = checkCompatible(c, withLocalTypeInference)
          val afterExtensionPredicate = compatible.flatMap(applyExtensionPredicate)
          afterExtensionPredicate.foreach { r =>
            if (!r.problems.contains(WrongTypeParameterInferred)) {
              val notMoreSpecific = mostSpecificUtil.notMoreSpecificThan(r)
              filteredCandidates = filteredCandidates.filter(notMoreSpecific)
              //this filter was added to make result deterministic
              results = results.filter(c => notMoreSpecific(c))
              results += r
            }
          }
        case None => filteredCandidates = Set.empty
      }
    }
    results.toSet
  }

  private def simpleConformanceCheck(c: ScalaResolveResult): Option[ScalaResolveResult] = {
    c.element match {
      case typeable: Typeable =>
        val subst = c.substitutor
        typeable.`type`() match {
          case Right(t) =>
            if (!subst(t).conforms(tp))
              reportWrong(c, TypeDoesntConformResult)
            else
              Some(c.copy(implicitReason = OkResult))
          case _ =>
            reportWrong(c, BadTypeResult)
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
    hadDependents:     Boolean
  ): Option[ScalaResolveResult] = {
    val fun = c.element.asInstanceOf[ScFunction]

    def wrongTypeParam(result: ImplicitResult): Some[ScalaResolveResult] = {
      Some(c.copy(problems = Seq(WrongTypeParameterInferred), implicitReason = result))
    }

    def reportParamNotFoundResult(implicitParams: Seq[ScalaResolveResult]): Option[ScalaResolveResult] = {
      reportWrong(c.copy(implicitParameters = implicitParams), ImplicitParameterNotFoundResult)
    }

    def noImplicitParametersResult(nonValueType: ScType): Some[ScalaResolveResult] = {
      val (valueType, typeParams) = inferValueType(nonValueType)
      val result = c.copy(
        implicitParameterType = Some(valueType),
        implicitReason = OkResult,
        unresolvedTypeParameters = Some(typeParams)
      )
      Some(result)
    }

    def fullResult(
      resType:          ScType,
      implicitParams:   Seq[ScalaResolveResult],
      checkConformance: Boolean = false
    ): Option[ScalaResolveResult] = {
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
        reportWrong(result, TypeDoesntConformResult, Seq(WrongTypeParameterInferred))
      else Some(result)
    }

    def wrongExtensionConversion(nonValueType: ScType): Option[ScalaResolveResult] = {
      extensionData.flatMap { data =>
        inferValueType(nonValueType) match {
          case (FunctionType(rt, _), _) =>
            val newCandidate = c.copy(implicitParameterType = Some(rt))
            if (extensionConversionCheck(data, newCandidate).isEmpty)
              wrongTypeParam(CantFindExtensionMethodResult)
            else None
          //this is not a function, when we still need to pass implicit?..
          case _ =>
            wrongTypeParam(UnhandledResult)
        }
      }
    }

    val nonValueType =
      try {
        val updated = updateNonValueType(nonValueType0)

        if (hadDependents) UndefinedType.revertDependentTypes(updated)
        else updated
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
        val (resType, implicitParams0) =
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

  @Measure
  private def checkFunctionType(
    c:                ScalaResolveResult,
    nonValueFunTypes: NonValueFunctionTypes
  ): Option[ScalaResolveResult] = {

    def compute(): Option[ScalaResolveResult] = {
      nonValueFunTypes.methodType match {
        case None =>
          Some(c.copy(implicitReason = OkResult))

        case Some(nonValueType0) =>
          try {
            updateImplicitParameters(c, c.substitutor(nonValueType0), nonValueFunTypes.hasImplicitClause, nonValueFunTypes.hadDependents)
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

  private def reportWrong(c: ScalaResolveResult, reason: ImplicitResult, problems: Seq[ApplicabilityProblem] = Seq.empty) = {
    if (fullInfo) Some(c.copy(problems = problems, implicitReason = reason))
    else None
  }

  private def isPredefConforms(fun: ScFunction) = {
    val name = fun.name
    val clazz = fun.containingClass
    (name == "conforms" || name == "$conforms") && clazz != null && clazz.qualifiedName == "scala.Predef"
  }

  @Measure
  private def checkFunctionByType(
    c:                      ScalaResolveResult,
    withLocalTypeInference: Boolean,
    checkFast:              Boolean
  ): Option[ScalaResolveResult] = {
    val fun = c.element.asInstanceOf[ScFunction]

    if (fun.hasTypeParameters && !withLocalTypeInference)
      return None

    val macroEvaluator = ScalaMacroEvaluator.getInstance(project)
    val typeFromMacro = macroEvaluator.checkMacro(fun, MacroContext(place, Some(tp)))

    val nonValueFunctionTypes =
      ImplicitCollector.cache(project).getNonValueTypes(fun, c.substitutor, typeFromMacro)

    nonValueFunctionTypes.undefinedType match {
      case Some(undefined: ScType) =>

        val undefinedConforms =
          isImplicitConversion && checkWeakConformance(undefined, tp) ||
            undefined.conforms(tp)

        if (undefinedConforms) {
          if (checkFast) Some(c)
          else checkFunctionType(c, nonValueFunctionTypes)
        }
        else {
          reportWrong(c, TypeDoesntConformResult)
        }
      case _ =>
        if (!withLocalTypeInference) reportWrong(c, BadTypeResult)
        else None
    }
  }

  private def applyExtensionPredicate(cand: ScalaResolveResult): Option[ScalaResolveResult] = {
    extensionData match {
      case None => Some(cand)
      case Some(data) =>
        extensionConversionCheck(data, cand).orElse {
          reportWrong(cand, CantFindExtensionMethodResult)
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

  private def checkWeakConformance(left: ScType, right: ScType): Boolean = {
    def function1Arg(scType: ScType): Option[(ScType, ScType)] = scType match {
      case ParameterizedType(ScDesignatorType(c: PsiClass), args) if args.size == 2 =>
        if (c.qualifiedName == "scala.Function1") (args.head, args.last).toOption
        else                                      None
      case _ => None
    }

    function1Arg(left) match {
      case Some((leftArg, leftRes)) =>
        function1Arg(right) match {
          case Some((rightArg, rightRes)) => rightArg.weakConforms(leftArg) && leftRes.conforms(rightRes)
          case _                          => false
        }
      case _ => false
    }
  }
}
