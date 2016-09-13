package org.jetbrains.plugins.scala
package lang.psi.implicits

import java.util.concurrent.ConcurrentMap

import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.project.Project
import com.intellij.psi._
import com.intellij.psi.util.PsiTreeUtil
import org.jetbrains.plugins.scala.caches.ScalaRecursionManager
import org.jetbrains.plugins.scala.caches.ScalaRecursionManager.RecursionMap
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiUtil
import org.jetbrains.plugins.scala.lang.psi.api.InferUtil.SafeCheckException
import org.jetbrains.plugins.scala.lang.psi.api.base.ScFieldId
import org.jetbrains.plugins.scala.lang.psi.api.base.patterns.ScBindingPattern
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScExpression
import org.jetbrains.plugins.scala.lang.psi.api.statements._
import org.jetbrains.plugins.scala.lang.psi.api.statements.params.{ScClassParameter, ScParameter}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.templates.{ScExtendsBlock, ScTemplateBody, ScTemplateParents}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.{ScMember, ScObject}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.{ScModifierListOwner, ScNamedElement, ScTypedDefinition}
import org.jetbrains.plugins.scala.lang.psi.api.{InferUtil, MacroInferUtil}
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiManager
import org.jetbrains.plugins.scala.lang.psi.implicits.ImplicitCollector._
import org.jetbrains.plugins.scala.lang.psi.types._
import org.jetbrains.plugins.scala.lang.psi.types.api._
import org.jetbrains.plugins.scala.lang.psi.types.api.designator._
import org.jetbrains.plugins.scala.lang.psi.types.nonvalue.{ScMethodType, ScTypePolymorphicType}
import org.jetbrains.plugins.scala.lang.psi.types.result.{Success, TypeResult, Typeable, TypingContext}
import org.jetbrains.plugins.scala.lang.refactoring.util.ScTypeUtil.AliasType
import org.jetbrains.plugins.scala.lang.resolve._
import org.jetbrains.plugins.scala.lang.resolve.processor.{BaseProcessor, ImplicitProcessor, MostSpecificUtil}
import org.jetbrains.plugins.scala.project.ProjectExt
import org.jetbrains.plugins.scala.settings.ScalaProjectSettings

import scala.Option.option2Iterable
import scala.annotation.tailrec
import scala.collection.Set
import scala.collection.immutable.HashSet

object ImplicitCollector {

  type Candidate = (ScalaResolveResult, ScSubstitutor)

  def cache(project: Project): ConcurrentMap[(PsiElement, ScType), Seq[ScalaResolveResult]] =
    ScalaPsiManager.instance(project).implicitCollectorCache

  def exprType(expr: ScExpression, fromUnder: Boolean): Option[ScType] = {
    expr.getTypeWithoutImplicits(fromUnderscore = fromUnder).toOption.map(_.tryExtractDesignatorSingleton)
  }

  sealed trait ImplicitResult

  case object NoResult extends ImplicitResult
  case object OkResult extends ImplicitResult
  case object TypeDoesntConformResult extends ImplicitResult
  case object BadTypeResult extends ImplicitResult
  case object CantFindExtensionMethodResult extends ImplicitResult
  case object DivergedImplicitResult extends ImplicitResult
  case object UnhandledResult extends ImplicitResult
  case object CantInferTypeParameterResult extends ImplicitResult
  case object ImplicitParameterNotFoundResult extends ImplicitResult
  case object FunctionForParameterResult extends ImplicitResult

  case class ImplicitState(place: PsiElement,
                           tp: ScType,
                           expandedTp: ScType,
                           coreElement: Option[ScNamedElement],
                           isImplicitConversion: Boolean,
                           searchImplicitsRecursively: Int,
                           extensionPredicate: Option[Candidate => Option[Candidate]],
                           previousRecursionState: Option[RecursionMap])

  var useOldImplicitCollector = false
}

/**
 * @param place        The call site
 * @param tp           Search for an implicit definition of this type. May have type variables.
 *
 * User: Alexander Podkhalyuzin
 * Date: 23.11.2009
 */
class ImplicitCollector(private var place: PsiElement,
                        tp: ScType,
                        expandedTp: ScType,
                        coreElement: Option[ScNamedElement],
                        isImplicitConversion: Boolean,
                        searchImplicitsRecursively: Int = 0,
                        extensionPredicate: Option[Candidate => Option[Candidate]] = None,
                        previousRecursionState: Option[RecursionMap] = None) {
  def this(state: ImplicitState) {
    this(state.place, state.tp, state.expandedTp, state.coreElement, state.isImplicitConversion,
       state.searchImplicitsRecursively, state.extensionPredicate, state.previousRecursionState)
  }

  private val searchStart = place

  lazy val collectorState: ImplicitState = ImplicitState(place, tp, expandedTp, coreElement, isImplicitConversion,
    searchImplicitsRecursively, extensionPredicate, Some(ScalaRecursionManager.recursionMap.get()))

  private val project = place.getProject
  private implicit val typeSystem = project.typeSystem

  private val clazz: Option[PsiClass] = tp.extractClass(project)
  private val possibleScalaFunction: Option[Int] = clazz.flatMap(possibleFunctionN)

  val mostSpecificUtil: MostSpecificUtil = MostSpecificUtil(place, 1)

  private var placeCalculated = false

  def isExtensionConversion = extensionPredicate.isDefined

  def collect(fullInfo: Boolean = false): Seq[ScalaResolveResult] = {
    def calc(): Seq[ScalaResolveResult] = {
      clazz match {
        case Some(c) if InferUtil.skipQualSet.contains(c.qualifiedName) => return Seq.empty
        case _ =>
      }
      val implicitCollectorCache = ImplicitCollector.cache(project)
      var result = implicitCollectorCache.get((place, tp))
      if (result != null && !fullInfo) return result
      ProgressManager.checkCanceled()
      var processor = new ImplicitParametersProcessor(withoutPrecedence = false, fullInfo)
      var placeForTreeWalkUp = place
      var lastParent: PsiElement = null
      var stop = false
      while (!stop) {
        if (placeForTreeWalkUp == null || !placeForTreeWalkUp.processDeclarations(processor,
          ResolveState.initial(), lastParent, place)) stop = true
        placeForTreeWalkUp match {
          case (_: ScTemplateBody | _: ScExtendsBlock) => //template body and inherited members are at the same level
          case _ => if (!processor.changedLevel) stop = true
        }
        if (!stop) {
          if (!placeCalculated) {
            place = placeForTreeWalkUp
            place match {
              case _: ScTemplateParents => placeCalculated = true
              case m: ScModifierListOwner if m.hasModifierProperty("implicit") =>
                placeCalculated = true //we need to check that, otherwise we will be outside
              case _ =>
            }
            if (!isExtensionConversion) result = implicitCollectorCache.get((place, tp))
            if (result != null && !fullInfo) return result
          }
          lastParent = placeForTreeWalkUp
          placeForTreeWalkUp = placeForTreeWalkUp.getContext
        }
      }

      val candidates: Seq[ScalaResolveResult] =
        if (fullInfo) Seq.empty //will be collected together with second part
        else processor.candidatesS.toSeq
      if (candidates.nonEmpty && !candidates.forall(!_.isApplicable())) return candidates

      if (!fullInfo) processor = new ImplicitParametersProcessor(withoutPrecedence = true, fullInfo)

      for (obj <- ScalaPsiUtil.collectImplicitObjects(expandedTp, project, place.getResolveScope)) {
        processor.processType(obj, place, ResolveState.initial())
      }

      val secondCandidates = processor.candidatesS.toSeq
      result =
        if (secondCandidates.isEmpty) candidates else secondCandidates
      if (!isExtensionConversion && !fullInfo) implicitCollectorCache.put((place, tp), result)
      result
    }

    previousRecursionState match {
      case Some(m) =>
        ScalaRecursionManager.usingPreviousRecursionMap(m) {
          calc()
        }
      case _ => calc()
    }
  }

  private def possibleFunctionN(clazz: PsiClass): Option[Int] = {
    clazz.qualifiedName match {
      case "java.lang.Object" | "scala.ScalaObject" => Some(-1)
      case s =>
        val prefix = "scala.Function"
        if (s.startsWith(prefix)) {
          val paramsNumber = s.stripPrefix(prefix)
          if (paramsNumber.nonEmpty && paramsNumber.forall(_.isDigit)) Some(paramsNumber.toInt)
          else None
        }
        else None
    }
  }

  private class ImplicitParametersProcessor(withoutPrecedence: Boolean, fullInfo: Boolean = false)
                                           (implicit override val typeSystem: TypeSystem)
    extends ImplicitProcessor(StdKinds.refExprLastRef, withoutPrecedence) {
    protected def getPlace: PsiElement = place

    private val applyExtensionPredicate: Candidate => Option[Candidate] = cand => {
      if (extensionPredicate.isEmpty) Some(cand)
      else {
        val (c, s) = cand
        extensionPredicate.get.apply((c, s)).orElse {
          reportWrong(c, s, CantFindExtensionMethodResult)
        }
      }
    }

    def execute(element: PsiElement, state: ResolveState): Boolean = {
      if (!kindMatches(element)) return true

      def addResultForElement(): Boolean = {
        val named = element.asInstanceOf[PsiNamedElement]
        val subst = state.get(BaseProcessor.FROM_TYPE_KEY) match {
          case null => getSubst(state)
          case t => getSubst(state).followUpdateThisType(t)
        }
        addResult(new ScalaResolveResult(named, subst, getImports(state), implicitSearchState = Some(collectorState)))
      }

      element match {
        case p: ScParameter if p.isImplicitParameter =>
          placeCalculated = true
          p match {
            case c: ScClassParameter if !isAccessible(c) => return true
            case _ =>
          }
          addResultForElement()
        case member: ScMember if member.hasModifierProperty("implicit") =>
          placeCalculated = true
          if (isAccessible(member)) addResultForElement()
        case _: ScBindingPattern | _: ScFieldId =>
          val member = ScalaPsiUtil.getContextOfType(element, true, classOf[ScValue], classOf[ScVariable]) match {
            case m: ScMember if m.hasModifierProperty("implicit") => m
            case _ => return true
          }
          if (isAccessible(member)) addResultForElement()
        case _ =>
      }

      true
    }

    private def isAccessible(member: ScMember): Boolean = {
      isPredefPriority || (member match {
        case fun: ScFunction =>
          ScImplicitlyConvertible.checkFucntionIsEligible(fun, place) && ResolveUtils.isAccessible(member, getPlace)
        case _ => ResolveUtils.isAccessible(member, getPlace)
      })
    }

    private def lowerInFileWithoutType(c: ScalaResolveResult) = {
      def lowerInFile(e: PsiElement) = e.containingFile == searchStart.containingFile &&
        ScalaPsiUtil.isInvalidContextOrder(searchStart, e, e.containingFile)

      c.getElement match {
        case fun: ScFunction if fun.returnTypeElement.isEmpty => lowerInFile(fun)
        case pattern @ ScalaPsiUtil.inNameContext(pd: ScPatternDefinition) if pd.typeElement.isEmpty => lowerInFile(pattern)
        case _ => false
      }
    }

    private def isContextAncestor(c: ScalaResolveResult) = {
      val nameContext = ScalaPsiUtil.nameContext(c.element)
      PsiTreeUtil.isContextAncestor(nameContext, place, false)
    }

    private def simpleConformanceCheck(c: ScalaResolveResult): Option[Candidate] = {
      c.element match {
        case typeable: Typeable =>
          val subst = c.substitutor
          typeable.getType(TypingContext.empty) match {
            case Success(t: ScType, _) =>
              if (!subst.subst(t).conforms(tp))
                reportWrong(c, subst, TypeDoesntConformResult)
              else
                Some(c.copy(implicitReason = OkResult), subst)
            case _ =>
              reportWrong(c, subst, BadTypeResult)
          }
        case _ => None
      }
    }

    override def candidatesS: Set[ScalaResolveResult] = {

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

            checkFunctionByType(c, withLocalTypeInference, checkFast)

          case _ =>
            if (withLocalTypeInference) None //only functions may have local type inference
            else simpleConformanceCheck(c)
        }
      }

      def isPlausible(c: ScalaResolveResult, withLocalTypeInference: Boolean) = checkCompatible(c, withLocalTypeInference, checkFast = true).isDefined

      def collectCompatibleCandidates(candidates: Set[ScalaResolveResult], withLocalTypeInference: Boolean): Set[Candidate] = {
        var filteredCandidates = candidates.filter(c => isPlausible(c, withLocalTypeInference))
        var results: Set[Candidate] = Set()

        while (filteredCandidates.nonEmpty) {
          val next = mostSpecificUtil.nextMostSpecific(filteredCandidates)
          next match {
            case Some(c) =>
              filteredCandidates = filteredCandidates - c
              val compatible = checkCompatible(c, withLocalTypeInference)
              val afterExtensionPredicate = compatible.flatMap(applyExtensionPredicate(_))
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

      def collectFullInfo(candidates: Set[ScalaResolveResult]): Set[ScalaResolveResult] = {
        val allCandidates =
          candidates.flatMap(c => checkCompatible(c, withLocalTypeInference = false)) ++
            candidates.flatMap(c => checkCompatible(c, withLocalTypeInference = true))
        val afterExtensionPredicate = allCandidates.flatMap(applyExtensionPredicate(_))
        afterExtensionPredicate.map(_._1)
      }

      val candidates = super.candidatesS.filterNot(c  => lowerInFileWithoutType(c) || isContextAncestor(c))

      if (fullInfo) collectFullInfo(candidates)
      else {
        val compatible = {
          //implicits found without local type inference have higher priority
          val withoutLocalTypeInference = collectCompatibleCandidates(candidates, withLocalTypeInference = false)

          if (withoutLocalTypeInference.nonEmpty) withoutLocalTypeInference
          else collectCompatibleCandidates(candidates, withLocalTypeInference = true)
        }

        mostSpecificUtil.mostSpecificForImplicitParameters(compatible) match {
          case Some(r) => HashSet(r)
          case _ => compatible.map(_._1)
        }
      }
    }

    private def inferValueType(tp: ScType): (ScType, Seq[TypeParameter]) = {
      if (isExtensionConversion) {
        tp match {
          case ScTypePolymorphicType(internalType, typeParams) =>
            val filteredTypeParams =
              typeParams.filter(tp => !tp.lowerType.v.equiv(Nothing) || !tp.upperType.v.equiv(Any))
            val newPolymorphicType = ScTypePolymorphicType(internalType, filteredTypeParams)
            val updated = newPolymorphicType.inferValueType.recursiveUpdate {
              case u: UndefinedType => (true, u.parameterType)
              case tp: ScType => (false, tp)
            }
            (updated, typeParams)
          case _ => (tp.inferValueType, Seq.empty)
        }
      } else tp match {
        case ScTypePolymorphicType(_, typeParams) => (tp.inferValueType, typeParams)
        case _ => (tp.inferValueType, Seq.empty)
      }
    }

    private def updateNonValueType(nonValueType0: ScType): TypeResult[ScType] = {
      InferUtil.updateAccordingToExpectedType(
        Success(nonValueType0, Some(place)),
        fromImplicitParameters = true,
        filterTypeParams = isImplicitConversion,
        expectedType = Some(tp),
        place,
        check = true
      )
    }

    private def updateImplicitParameters(c: ScalaResolveResult, nonValueType0: ScType, hasImplicitClause: Boolean): Option[Candidate] = {
      val fun = c.element.asInstanceOf[ScFunction]
      val subst = c.substitutor

      def wrongTypeParam(result: ImplicitResult): Some[Candidate] = {
        Some(c.copy(problems = Seq(WrongTypeParameterInferred), implicitReason = result), subst)
      }

      def reportParamNotFoundResult(implicitParams: Seq[ScalaResolveResult]): Option[Candidate] = {
        reportWrong(c.copy(implicitParameters = implicitParams), subst, ImplicitParameterNotFoundResult)
      }

      def updateTypeWithImplicitParameters(nonValueType: ScType) = {
        InferUtil.updateTypeWithImplicitParameters(nonValueType, place, Some(fun), check = !fullInfo, searchImplicitsRecursively + 1, fullInfo)
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

      def fullResult(resType: ScType, implicitParams: Seq[ScalaResolveResult]): Some[Candidate] = {
        val (valueType, typeParams) = inferValueType(resType)

        val allImportsUsed = implicitParams.map(_.importsUsed).foldLeft(c.importsUsed)(_ ++ _)

        val result = c.copy(
          implicitParameterType = Some(valueType),
          implicitParameters = implicitParams,
          implicitReason = OkResult,
          unresolvedTypeParameters = Some(typeParams),
          importsUsed = allImportsUsed
        )
        Some(result, subst)
      }

      def wrongExtensionConversion(nonValueType: ScType): Option[Candidate] = {
        extensionPredicate.flatMap { predicate =>
          inferValueType(nonValueType) match {
            case (FunctionType(rt, _), _) =>
              if (predicate(c.copy(implicitParameterType = Some(rt)), subst).isEmpty)
                wrongTypeParam(CantFindExtensionMethodResult)
              else None
            //this is not a function, when we still need to pass implicit?..
            case _ =>
              wrongTypeParam(UnhandledResult)
          }
        }
      }

      val nonValueType: ScType =
        try updateNonValueType(nonValueType0) match {
          case Success(tpe, _) => tpe
          case _ => return wrongTypeParam(BadTypeResult)
        }
        catch {
          case _: SafeCheckException => return wrongTypeParam(CantInferTypeParameterResult)
        }

      val depth = ScalaProjectSettings.getInstance(project).getImplicitParametersSearchDepth
      val notTooDeepSearch = depth < 0 || searchImplicitsRecursively < depth

      if (hasImplicitClause && notTooDeepSearch) {

        wrongExtensionConversion(nonValueType) match {
          case Some(errorResult) => return Some(errorResult)
          case None =>
        }

        val (resType, implicitParams0) =
          try updateTypeWithImplicitParameters(nonValueType)
          catch {
            case _: SafeCheckException => return wrongTypeParam(CantInferTypeParameterResult)
          }

        val implicitParams = implicitParams0.getOrElse(Seq.empty)

        if (implicitParams.exists(_.name == InferUtil.notFoundParameterName))
          reportParamNotFoundResult(implicitParams)
        else
          fullResult(resType, implicitParams)

      } else {
        noImplicitParametersResult(nonValueType)
      }
    }

    def checkFunctionType(fun: ScFunction, ret: ScType, c: ScalaResolveResult): Option[Candidate] = {
      val subst = c.substitutor

      def compute(): Option[Candidate] = {
        val typeParameters = fun.typeParameters
        val implicitClause = fun.effectiveParameterClauses.lastOption.filter(_.isImplicit)
        if (typeParameters.isEmpty && implicitClause.isEmpty) Some(c.copy(implicitReason = OkResult), subst)
        else {
          val methodType = implicitClause.map(li => subst.subst(ScMethodType(ret, li.getSmartParameters, isImplicit = true)
          (project, place.getResolveScope))).getOrElse(ret)
          val polymorphicTypeParameters = typeParameters.map(TypeParameter(_))

          val nonValueType0: ScType =
            if (polymorphicTypeParameters.isEmpty) methodType
            else ScTypePolymorphicType(methodType, polymorphicTypeParameters)

          try updateImplicitParameters(c, nonValueType0, implicitClause.isDefined)
          catch {
            case _: SafeCheckException =>
              Some(c.copy(problems = Seq(WrongTypeParameterInferred), implicitReason = UnhandledResult), subst)
          }
        }
      }
      import org.jetbrains.plugins.scala.caches.ScalaRecursionManager._

      if (isImplicitConversion) compute()
      else {
        val coreTypeForTp = coreType(tp)

        def equivOrDominates(tp: ScType, found: ScType): Boolean =
          found.equiv(tp, new ScUndefinedSubstitutor(), falseUndef = false)._1 || dominates(tp, found)

        val checkAdd: (ScType, Seq[ScType]) => Boolean = (tp, searches) => !searches.exists(equivOrDominates(tp, _))

        doComputations(coreElement.getOrElse(place), checkAdd, coreTypeForTp, compute(), IMPLICIT_PARAM_TYPES_KEY) match {
          case Some(res) => res
          case None => reportWrong(c, subst, DivergedImplicitResult)
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
      val typeParameters = fun.typeParameters.map(_.name)
      var hasTypeParametersInType = false
      funType.recursiveUpdate {
        case tp@TypeParameterType(_, _, _, _) if typeParameters.contains(tp.name) =>
          hasTypeParametersInType = true
          (true, tp)
        case tp: ScType if hasTypeParametersInType => (true, tp)
        case tp: ScType => (false, tp)
      }
      hasTypeParametersInType
    }

    private def substedFunType(fun: ScFunction, funType: ScType, subst: ScSubstitutor, withLocalTypeInference: Boolean, noReturnType: Boolean): Option[ScType] = {
      if (!fun.hasTypeParameters) Some(subst.subst(funType))
      else if (noReturnType) {
        val inferredSubst = subst.followed(ScalaPsiUtil.inferMethodTypesArgs(fun, subst))
        Some(inferredSubst.subst(funType))
      }
      else {
        val hasTypeParametersInType: Boolean = hasTypeParamsInType(fun, funType)
        if (withLocalTypeInference && hasTypeParametersInType) {
          val inferredSubst = subst.followed(ScalaPsiUtil.inferMethodTypesArgs(fun, subst))
          Some(inferredSubst.subst(funType))
        } else if (!withLocalTypeInference && !hasTypeParametersInType) {
          Some(subst.subst(funType))
        } else None
      }
    }

    private def checkFunctionByType(c: ScalaResolveResult, withLocalTypeInference: Boolean, checkFast: Boolean): Option[Candidate] = {
      val fun = c.element.asInstanceOf[ScFunction]
      val subst = c.substitutor

      def checkFunctionByTypeInner(noReturnType: Boolean): Option[Candidate] = {
        val ft =
          if (noReturnType) fun.getTypeNoImplicits(Success(Nothing, Some(getPlace)))
          else fun.getTypeNoImplicits

        ft match {
          case Success(_funType: ScType, _) =>
            val funType = MacroInferUtil.checkMacro(fun, Some(tp), place) getOrElse _funType

            val substedFunTp = substedFunType(fun, funType, subst, withLocalTypeInference, noReturnType) match {
              case Some(t) => t
              case None => return None
            }

            if (isExtensionConversion && argsConformWeakly(substedFunTp, tp) || (substedFunTp conforms tp)) {
              if (checkFast || noReturnType) Some(c, ScSubstitutor.empty)
              else checkFunctionType(fun, substedFunTp, c)
            }
            else if (noReturnType) Some(c, ScSubstitutor.empty)
            else {
              substedFunTp match {
                case FunctionType(ret, params) if params.isEmpty =>
                  if (!ret.conforms(tp)) None
                  else if (checkFast) Some(c, ScSubstitutor.empty)
                  else checkFunctionType(fun, ret, c)
                case _ =>
                  reportWrong(c, subst, TypeDoesntConformResult)
              }
            }
          case _ =>
            if (!withLocalTypeInference) reportWrong(c, subst, BadTypeResult)
            else None
        }
      }

      def applicableParameters(): Boolean = checkFunctionByTypeInner(noReturnType = true).isDefined
      def checkFullType() = checkFunctionByTypeInner(noReturnType = false)

      if (isExtensionConversion && !fullInfo) {
        if (applicableParameters()) checkFullType()
        else None
      }
      else checkFullType()
    }
  }

  private def abstractsToUpper(tp: ScType): ScType = {
    val noAbstracts = tp.recursiveUpdate {
      case ScAbstractType(_, _, upper) => (true, upper)
      case t => (false, t)
    }

    @tailrec
    def updateAliases(tp: ScType): ScType = {
      var updated = false
      val res = tp.recursiveUpdate { t =>
        t.isAliasType match {
          case Some(AliasType(ta, _, upper)) =>
            updated = true
            //todo: looks like a hack. Imagine type A <: B; type B <: List[A];
            val nonRecursiveUpper = upper.map { upper =>
              upper.recursiveUpdate { t =>
                t.isAliasType match {
                  case Some(AliasType(`ta`, _, _)) => (true, Any)
                  case _ => (false, t)
                }
              }
            }
            (true, nonRecursiveUpper.getOrAny)
          case _ => (false, t)
        }
      }
      if (!updated) tp
      else updateAliases(res)
    }
    updateAliases(noAbstracts)
  }

  private def coreType(tp: ScType): ScType = {
    tp match {
      case ScCompoundType(comps, _, _) => abstractsToUpper(ScCompoundType(comps, Map.empty, Map.empty)).removeUndefines()
      case ScExistentialType(quant, wilds) => abstractsToUpper(ScExistentialType(quant.recursiveUpdate {
        case ScExistentialArgument(name, _, _, _) => wilds.find(_.name == name).map(w => (true, w.upper)).getOrElse((false, tp))
        case other => (false, other)
      }, wilds)).removeUndefines()
      case _ => abstractsToUpper(tp).removeUndefines()
    }
  }

  private def dominates(t: ScType, u: ScType): Boolean = {
    complexity(t) > complexity(u) && topLevelTypeConstructors(t).intersect(topLevelTypeConstructors(u)).nonEmpty
  }

  private def topLevelTypeConstructors(tp: ScType): Set[ScType] = {
    tp match {
      case ScProjectionType(_, element, _) => Set(ScDesignatorType(element))
      case ParameterizedType(designator, _) => Set(designator)
      case tp@ScDesignatorType(_: ScObject) => Set(tp)
      case ScDesignatorType(v: ScTypedDefinition) =>
        val valueType: ScType = v.getType(TypingContext.empty).getOrAny
        topLevelTypeConstructors(valueType)
      case ScCompoundType(comps, _, _) => comps.flatMap(topLevelTypeConstructors).toSet
      case _ => Set(tp)
    }
  }

  private def complexity(tp: ScType): Int = {
    tp match {
      case ScProjectionType(proj, _, _) => 1 + complexity(proj)
      case ParameterizedType(_, args) => 1 + args.foldLeft(0)(_ + complexity(_))
      case ScDesignatorType(_: ScObject) => 1
      case ScDesignatorType(v: ScTypedDefinition) =>
        val valueType: ScType = v.getType(TypingContext.empty).getOrAny
        1 + complexity(valueType)
      case ScCompoundType(comps, _, _) => comps.foldLeft(0)(_ + complexity(_))
      case _ => 1
    }
  }

  private def argsConformWeakly(left: ScType, right: ScType)(implicit typeSystem: TypeSystem): Boolean = {
    (left, right) match {
      case (leftFun: ScParameterizedType, rightFun: ScParameterizedType) =>
        leftFun.designator.canonicalText == "_root_.scala.Function1" &&
          rightFun.designator.canonicalText == "_root_.scala.Function1" &&
          rightFun.typeArguments.nonEmpty && leftFun.typeArguments.nonEmpty &&
          (rightFun.typeArguments.head weakConforms leftFun.typeArguments.head)
      case _ => false
    }
  }
}
