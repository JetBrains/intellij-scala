package org.jetbrains.plugins.scala
package lang.psi.implicits

import com.intellij.openapi.progress.ProgressManager
import com.intellij.psi._
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.util.containers.ContainerUtil
import org.jetbrains.plugins.scala.caches.ScalaRecursionManager
import org.jetbrains.plugins.scala.caches.ScalaRecursionManager.RecursionMap
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.psi.api.InferUtil.SafeCheckException
import org.jetbrains.plugins.scala.lang.psi.api.base.ScFieldId
import org.jetbrains.plugins.scala.lang.psi.api.base.patterns.ScBindingPattern
import org.jetbrains.plugins.scala.lang.psi.api.base.types.ScExistentialClause
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScExpression
import org.jetbrains.plugins.scala.lang.psi.api.statements._
import org.jetbrains.plugins.scala.lang.psi.api.statements.params.{ScClassParameter, ScParameter}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.templates.{ScExtendsBlock, ScTemplateBody, ScTemplateParents}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.{ScMember, ScObject}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.{ScModifierListOwner, ScNamedElement, ScTypedDefinition}
import org.jetbrains.plugins.scala.lang.psi.api.{InferUtil, MacroInferUtil}
import org.jetbrains.plugins.scala.lang.psi.implicits.ImplicitCollector._
import org.jetbrains.plugins.scala.lang.psi.types._
import org.jetbrains.plugins.scala.lang.psi.types.api.TypeSystem
import org.jetbrains.plugins.scala.lang.psi.types.nonvalue.{ScMethodType, ScTypePolymorphicType, TypeParameter}
import org.jetbrains.plugins.scala.lang.psi.types.result.{Success, TypeResult, TypingContext}
import org.jetbrains.plugins.scala.lang.psi.{ScalaPsiUtil, types}
import org.jetbrains.plugins.scala.lang.refactoring.util.ScTypeUtil.AliasType
import org.jetbrains.plugins.scala.lang.resolve._
import org.jetbrains.plugins.scala.lang.resolve.processor.{BaseProcessor, ImplicitProcessor, MostSpecificUtil}
import org.jetbrains.plugins.scala.settings.ScalaProjectSettings

import scala.annotation.tailrec
import scala.collection.immutable.HashSet
import scala.collection.mutable.ArrayBuffer

object ImplicitCollector {
  val cache = ContainerUtil.newConcurrentMap[(PsiElement, ScType), Seq[ScalaResolveResult]]()

  def exprType(expr: ScExpression, fromUnder: Boolean): Option[ScType] = {
    expr.getTypeWithoutImplicits(fromUnderscore = fromUnder).toOption.map {
      case tp =>
        ScType.extractDesignatorSingletonType(tp) match {
          case Some(res) => res
          case _ => tp
        }
    }
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

  case class ImplicitState(place: PsiElement, tp: ScType, expandedTp: ScType,
                           coreElement: Option[ScNamedElement], isImplicitConversion: Boolean,
                           isExtensionConversion: Boolean, searchImplicitsRecursively: Int,
                           predicate: Option[(ScalaResolveResult, ScSubstitutor) =>
                             Option[(ScalaResolveResult, ScSubstitutor)]],
                           previousRecursionState: Option[RecursionMap])
}

/**
 * @param place        The call site
 * @param tp           Search for an implicit definition of this type. May have type variables.
 *
 * User: Alexander Podkhalyuzin
 * Date: 23.11.2009
 */
class ImplicitCollector(private var place: PsiElement, tp: ScType, expandedTp: ScType,
                        coreElement: Option[ScNamedElement], isImplicitConversion: Boolean,
                        isExtensionConversion: Boolean, searchImplicitsRecursively: Int = 0,
                        predicate: Option[(ScalaResolveResult, ScSubstitutor) =>
                          Option[(ScalaResolveResult, ScSubstitutor)]] = None,
                        previousRecursionState: Option[RecursionMap] = None) {
  def this(state: ImplicitState) {
    this(state.place, state.tp, state.expandedTp, state.coreElement, state.isImplicitConversion,
      state.isExtensionConversion, state.searchImplicitsRecursively, state.predicate, state.previousRecursionState)
  }

  lazy val collectorState: ImplicitState = ImplicitState(place, tp, expandedTp, coreElement, isImplicitConversion,
    isExtensionConversion, searchImplicitsRecursively, predicate, Some(ScalaRecursionManager.recursionMap.get()))

  private var placeCalculated = false

  def collect(fullInfo: Boolean = false)
             (implicit typeSystem: TypeSystem): Seq[ScalaResolveResult] = {
    def calc(): Seq[ScalaResolveResult] = {
      ScType.extractClass(tp, Some(place.getProject)) match {
        case Some(clazz) if InferUtil.skipQualSet.contains(clazz.qualifiedName) => return Seq.empty
        case _ =>
      }
      var result = ImplicitCollector.cache.get((place, tp))
      if (result != null && !fullInfo) return result
      ProgressManager.checkCanceled()
      var processor = new ImplicitParametersProcessor(false)
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
              case e: ScTemplateParents => placeCalculated = true
              case m: ScModifierListOwner if m.hasModifierProperty("implicit") =>
                placeCalculated = true //we need to check that, otherwise we will be outside
              case _ =>
            }
            if (predicate.isEmpty) result = ImplicitCollector.cache.get((place, tp))
            if (result != null && !fullInfo) return result
          }
          lastParent = placeForTreeWalkUp
          placeForTreeWalkUp = placeForTreeWalkUp.getContext
        }
      }

      val candidates: Seq[ScalaResolveResult] =
        if (fullInfo) Seq.empty //will be collected together with second part
        else processor.candidatesS(fullInfo).toSeq
      if (candidates.nonEmpty && !candidates.forall(!_.isApplicable())) return candidates

      if (!fullInfo) processor = new ImplicitParametersProcessor(true)

      for (obj <- ScalaPsiUtil.collectImplicitObjects(expandedTp, place.getProject, place.getResolveScope)) {
        processor.processType(obj, place, ResolveState.initial())
      }

      val secondCandidates = processor.candidatesS(fullInfo).toSeq
      result =
        if (secondCandidates.isEmpty) candidates else secondCandidates
      if (predicate.isEmpty && !fullInfo) ImplicitCollector.cache.put((place, tp), result)
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

  class ImplicitParametersProcessor(withoutPrecedence: Boolean)(implicit override val typeSystem: TypeSystem)
    extends ImplicitProcessor(StdKinds.refExprLastRef, withoutPrecedence) {
    protected def getPlace: PsiElement = place

    def execute(element: PsiElement, state: ResolveState): Boolean = {
      if (!kindMatches(element)) return true
      val named = element.asInstanceOf[PsiNamedElement]
      def fromType: Option[ScType] = state.get(BaseProcessor.FROM_TYPE_KEY).toOption
      lazy val subst: ScSubstitutor = fromType match {
        case Some(t) => getSubst(state).followUpdateThisType(t)
        case _ => getSubst(state)
      }
      named match {
        case o: ScObject if o.hasModifierProperty("implicit") =>
          placeCalculated = true
          if (!isPredefPriority && !ResolveUtils.isAccessible(o, getPlace)) return true
          addResult(new ScalaResolveResult(o, subst, getImports(state), implicitSearchState = Some(collectorState)))
        case param: ScParameter if param.isImplicitParameter =>
          placeCalculated = true
          param match {
            case c: ScClassParameter =>
              if (!isPredefPriority && !ResolveUtils.isAccessible(c, getPlace)) return true
            case _ =>
          }
          addResult(new ScalaResolveResult(param, subst, getImports(state), implicitSearchState = Some(collectorState)))
        case f: ScFieldId =>
          val memb = ScalaPsiUtil.getContextOfType(f, true, classOf[ScValue], classOf[ScVariable])
          memb match {
            case memb: ScMember if memb.hasModifierProperty("implicit") =>
              placeCalculated = true
              if (!isPredefPriority && !ResolveUtils.isAccessible(memb, getPlace)) return true
              addResult(new ScalaResolveResult(named, subst, getImports(state), implicitSearchState = Some(collectorState)))
            case _ =>
          }
        case patt: ScBindingPattern =>
          val memb = ScalaPsiUtil.getContextOfType(patt, true, classOf[ScValue], classOf[ScVariable])
          memb match {
            case memb: ScMember if memb.hasModifierProperty("implicit") =>
              placeCalculated = true
              if (!isPredefPriority && !ResolveUtils.isAccessible(memb, getPlace)) return true
              addResult(new ScalaResolveResult(named, subst, getImports(state), implicitSearchState = Some(collectorState)))
            case _ =>
          }
        case function: ScFunction if function.hasModifierProperty("implicit") =>
          placeCalculated = true
          if (isPredefPriority || (ScImplicitlyConvertible.checkFucntionIsEligible(function, place) &&
              ResolveUtils.isAccessible(function, getPlace))) {
            addResult(new ScalaResolveResult(named, subst, getImports(state), implicitSearchState = Some(collectorState)))
          }
        case _ =>
      }
      true
    }

    override def candidatesS: collection.Set[ScalaResolveResult] = candidatesS(fullInfo = false)

    def candidatesS(fullInfo: Boolean): collection.Set[ScalaResolveResult] = {
      val clazz = ScType.extractClass(tp)
      def forMap(c: ScalaResolveResult, withLocalTypeInference: Boolean, checkFast: Boolean): Option[(ScalaResolveResult, ScSubstitutor)] = {
        ProgressManager.checkCanceled()
        val subst = c.substitutor
        (c.element match {
          case o: ScObject if !withLocalTypeInference && !PsiTreeUtil.isContextAncestor(o, place, false) =>
            o.getType(TypingContext.empty) match {
              case Success(objType: ScType, _) =>
                if (!subst.subst(objType).conforms(tp))
                  if (fullInfo) Some(c.copy(implicitReason = TypeDoesntConformResult), subst)
                  else None
                else Some(c.copy(implicitReason = OkResult), subst)
              case _ =>
                if (fullInfo) Some(c.copy(implicitReason = BadTypeResult), subst)
                else None
            }
          case param: ScParameter if !withLocalTypeInference && !PsiTreeUtil.isContextAncestor(param, place, false) =>
            param.getType(TypingContext.empty) match {
              case Success(paramType: ScType, _) =>
                if (!subst.subst(paramType).conforms(tp))
                  if (fullInfo) Some(c.copy(implicitReason = TypeDoesntConformResult), subst)
                  else None
                else Some(c.copy(implicitReason = OkResult), subst)
              case _ =>
                if (fullInfo) Some(c.copy(implicitReason = BadTypeResult), subst)
                else None
            }
          case patt: ScBindingPattern
            if !withLocalTypeInference && !PsiTreeUtil.isContextAncestor(ScalaPsiUtil.nameContext(patt), place, false) =>
            patt.getType(TypingContext.empty) match {
              case Success(pattType: ScType, _) if !withLocalTypeInference =>
                if (!subst.subst(pattType).conforms(tp))
                  if (fullInfo) Some(c.copy(implicitReason = TypeDoesntConformResult), subst)
                  else None
                else Some(c.copy(implicitReason = OkResult), subst)
              case _ =>
                if (fullInfo) Some(c.copy(implicitReason = BadTypeResult), subst)
                else None
            }
          case f: ScFieldId
            if !withLocalTypeInference && !PsiTreeUtil.isContextAncestor(ScalaPsiUtil.nameContext(f), place, false) =>
            f.getType(TypingContext.empty) match {
              case Success(fType: ScType, _) =>
                if (!subst.subst(fType).conforms(tp))
                  if (fullInfo) Some(c.copy(implicitReason = TypeDoesntConformResult), subst)
                  else None
                else Some(c.copy(implicitReason = OkResult), subst)
              case _ =>
                if (fullInfo) Some(c.copy(implicitReason = BadTypeResult), subst)
                else None
            }
          case fun: ScFunction if !PsiTreeUtil.isContextAncestor(fun, place, false) =>
            if (isImplicitConversion && (fun.name == "conforms" || fun.name == "$conforms") &&
              fun.containingClass != null && fun.containingClass.qualifiedName == "scala.Predef") return None
            if (!fun.hasTypeParameters && withLocalTypeInference) return None

            val oneImplicit = fun.effectiveParameterClauses.length == 1 && fun.effectiveParameterClauses.head.isImplicit
            //to avoid checking implicit functions in case of simple implicit parameter search
            if (!oneImplicit && fun.effectiveParameterClauses.nonEmpty) {
              clazz match {
                case Some(cl) =>
                  val clause = fun.paramClauses.clauses.head
                  val funNum = clause.parameters.length
                  val qName = "scala.Function" + funNum
                  val classQualifiedName = cl.qualifiedName
                  if (classQualifiedName != qName && classQualifiedName != "java.lang.Object" &&
                    classQualifiedName != "scala.ScalaObject") {
                    if (fullInfo)
                      return Some(c.copy(problems = Seq(WrongTypeParameterInferred),
                        implicitReason = FunctionForParameterResult), subst)
                    else return None
                  }
                case _ =>
              }
            }

            def checkForFunctionType(noReturnType: Boolean): Option[(ScalaResolveResult, ScSubstitutor)] = {
              val ft =
                if (noReturnType) fun.getTypeNoImplicits(TypingContext.empty, Success(types.Nothing, Some(getPlace)))
                else fun.getTypeNoImplicits(TypingContext.empty)
              ft match {
                case Success(_funType: ScType, _) =>
                  def checkType(ret: ScType): Option[(ScalaResolveResult, ScSubstitutor)] = {
                    def compute(): Option[(ScalaResolveResult, ScSubstitutor)] = {
                      val typeParameters = fun.typeParameters
                      val lastImplicit = fun.effectiveParameterClauses.lastOption.flatMap {
                        case clause if clause.isImplicit => Some(clause)
                        case _ => None
                      }
                      if (typeParameters.isEmpty && lastImplicit.isEmpty) Some(c.copy(implicitReason = OkResult), subst)
                      else {
                        val methodType = lastImplicit.map(li => subst.subst(ScMethodType(ret, li.getSmartParameters, isImplicit = true)
                          (place.getProject, place.getResolveScope))).getOrElse(ret)
                        val polymorphicTypeParameters = typeParameters.map(new TypeParameter(_))
                        def inferValueType(tp: ScType): (ScType, Seq[TypeParameter]) = {
                          if (isExtensionConversion) {
                            tp match {
                              case ScTypePolymorphicType(internalType, typeParams) =>
                                val filteredTypeParams =
                                  typeParams.filter(tp => !tp.lowerType().equiv(types.Nothing) || !tp.upperType().equiv(types.Any))
                                val newPolymorphicType = ScTypePolymorphicType(internalType, filteredTypeParams)
                                (newPolymorphicType.inferValueType.recursiveUpdate {
                                  case u: ScUndefinedType => (true, u.tpt)
                                  case tp: ScType => (false, tp)
                                }, typeParams)
                              case _ => (tp.inferValueType, Seq.empty)
                            }
                          } else tp match {
                            case ScTypePolymorphicType(internalType, typeParams) =>
                              (tp.inferValueType, typeParams)
                            case _ => (tp.inferValueType, Seq.empty)
                          }
                        }
                        var nonValueType: TypeResult[ScType] =
                          Success(if (polymorphicTypeParameters.isEmpty) methodType
                          else ScTypePolymorphicType(methodType, polymorphicTypeParameters), Some(place))
                        try {
                          def reportWrong(result: ImplicitResult): Some[(ScalaResolveResult, ScSubstitutor)] = {
                            Some(c.copy(problems = Seq(WrongTypeParameterInferred),
                              implicitReason = result), subst)
                          }
                          def updateImplicitParameters(): Some[(ScalaResolveResult, ScSubstitutor)] = {
                            val expected = Some(tp)
                            try {
                              nonValueType = InferUtil.updateAccordingToExpectedType(nonValueType,
                                fromImplicitParameters = true, filterTypeParams = isImplicitConversion, expected, place, check = true)
                            } catch {
                              case e: SafeCheckException => return reportWrong(CantInferTypeParameterResult)
                            }

                            val depth = ScalaProjectSettings.getInstance(place.getProject).getImplicitParametersSearchDepth
                            if (lastImplicit.isDefined &&
                              (depth < 0 || searchImplicitsRecursively < depth)) {
                              predicate match {
                                case Some(predicateFunction) if isExtensionConversion =>
                                  inferValueType(nonValueType.getOrElse(return reportWrong(BadTypeResult))) match {
                                    case (ScFunctionType(rt, _), _) =>
                                      if (predicateFunction(c.copy(implicitParameterType = Some(rt)), subst).isEmpty)
                                        return reportWrong(CantFindExtensionMethodResult)
                                    //this is not a function, when we still need to pass implicit?..
                                    case _ =>
                                      return reportWrong(UnhandledResult)                                  
                                  }
                                case _ =>
                              }
                              val (resType, results) = 
                                try {
                                  InferUtil.updateTypeWithImplicitParameters(nonValueType.getOrElse(return reportWrong(BadTypeResult)),
                                    place, Some(fun), check = !fullInfo, searchImplicitsRecursively + 1, fullInfo)
                                } catch {
                                  case e: SafeCheckException => return reportWrong(CantInferTypeParameterResult)
                                }
                              if (fullInfo && results.exists(_.exists(_.name == InferUtil.notFoundParameterName)))
                                return Some(c.copy(implicitParameters = results.getOrElse(Seq.empty),
                                  implicitReason = ImplicitParameterNotFoundResult), subst)
                              val (valueType, typeParams) = inferValueType(resType)
                              def addImportsUsed(result: ScalaResolveResult, results: Seq[ScalaResolveResult]): ScalaResolveResult = {
                                results.foldLeft(result) {
                                  case (r1: ScalaResolveResult, r2: ScalaResolveResult) => r1.copy(importsUsed = r1.importsUsed ++ r2.importsUsed)
                                }
                              }
                              Some(addImportsUsed(c.copy(implicitParameterType = Some(valueType),
                                implicitParameters = results.getOrElse(Seq.empty), implicitReason = OkResult,
                                unresolvedTypeParameters = Some(typeParams)),
                                results.getOrElse(Seq.empty)), subst)
                            } else {
                              val (valueType, typeParams) = inferValueType(nonValueType.getOrElse(return reportWrong(BadTypeResult)))
                              Some(c.copy(implicitParameterType = Some(valueType),
                                implicitReason = OkResult, unresolvedTypeParameters = Some(typeParams)), subst)
                            }
                          }

                          updateImplicitParameters()
                        } catch {
                          case e: SafeCheckException => 
                            Some(c.copy(problems = Seq(WrongTypeParameterInferred), implicitReason = UnhandledResult), subst)
                        }
                      }
                    }
                    import org.jetbrains.plugins.scala.caches.ScalaRecursionManager._

                    if (isImplicitConversion) compute()
                    else {
                      val coreTypeForTp = coreType(tp)
                      doComputations(coreElement.getOrElse(place), (tp: Object, searches: Seq[Object]) => {
                        !searches.exists {
                          case t: ScType if tp.isInstanceOf[ScType] =>
                            if (t.equiv(tp.asInstanceOf[ScType], new ScUndefinedSubstitutor(), falseUndef = false)._1) true
                            else dominates(tp.asInstanceOf[ScType], t)
                          case _ => false
                        }
                      }, coreTypeForTp, compute(), IMPLICIT_PARAM_TYPES_KEY) match {
                        case Some(res) => res
                        case None =>
                          if (fullInfo) Some(c.copy(implicitReason = DivergedImplicitResult), subst)
                          else None
                      }
                    }
                  }

                  val funType = if (MacroInferUtil.isMacro(fun).isDefined) {
                    MacroInferUtil.checkMacro(fun, Some(tp), place) match {
                      case Some(newTp) => newTp
                      case _ => _funType
                    }
                  } else _funType
                  var substedFunType: ScType = funType

                  if (fun.hasTypeParameters && noReturnType) {
                    val inferredSubst = subst.followed(ScalaPsiUtil.inferMethodTypesArgs(fun, subst))
                    substedFunType = inferredSubst.subst(funType)
                  } else if (fun.hasTypeParameters) {
                    val typeParameters = fun.typeParameters.map(_.name)
                    var hasTypeParametersInType = false
                    funType.recursiveUpdate {
                      case tp@ScTypeParameterType(name, _, _, _, _) if typeParameters.contains(name) =>
                        hasTypeParametersInType = true
                        (true, tp)
                      case tp: ScType if hasTypeParametersInType => (true, tp)
                      case tp: ScType => (false, tp)
                    }
                    if (withLocalTypeInference && hasTypeParametersInType) {
                      val inferredSubst = subst.followed(ScalaPsiUtil.inferMethodTypesArgs(fun, subst))
                      substedFunType = inferredSubst.subst(funType)
                    } else if (!withLocalTypeInference && !hasTypeParametersInType) {
                      substedFunType = subst.subst(funType)
                    } else return None
                  } else {
                    substedFunType = subst.subst(funType)
                  }

                  if (substedFunType conforms tp) {
                    if (checkFast || noReturnType) Some(c, ScSubstitutor.empty)
                    else checkType(substedFunType)
                  } else if (noReturnType) Some(c, ScSubstitutor.empty) else {
                    substedFunType match {
                      case ScFunctionType(ret, params) if params.isEmpty =>
                        if (!ret.conforms(tp)) None
                        else if (checkFast) Some(c, ScSubstitutor.empty)
                        else checkType(ret)
                      case _ =>
                        if (fullInfo) Some(c.copy(implicitReason = TypeDoesntConformResult), subst)
                        else None
                    }
                  }
                case _ =>
                  if (fullInfo && !withLocalTypeInference) Some(c.copy(implicitReason = BadTypeResult), subst)
                  else None
              }
            }

            if (isExtensionConversion && !fullInfo) {
              checkForFunctionType(noReturnType = true) match {
                case None => None
                case _ => checkForFunctionType(noReturnType = false)
              }
            } else checkForFunctionType(noReturnType = false)
          case _ => None
        }) match {
          case Some((result, resultSubst)) if predicate.isDefined && !withLocalTypeInference =>
            val checkPredicate = predicate.get
            checkPredicate(result, resultSubst) match {
              case None if fullInfo => Some(result.copy(implicitReason = CantFindExtensionMethodResult), resultSubst)
              case res => res
            }
          case res => res
        }
      }

      val candidates = super.candidatesS

      val mostSpecific: MostSpecificUtil = new MostSpecificUtil(place, 1)

      def mapCandidates(withLocalTypeInference: Boolean): collection.Set[(ScalaResolveResult, ScSubstitutor)] = {
        var candidatesSeq = candidates.toSeq.filter(c => forMap(c, withLocalTypeInference, checkFast = true).isDefined)
        val results: ArrayBuffer[(ScalaResolveResult, ScSubstitutor)] = new ArrayBuffer[(ScalaResolveResult, ScSubstitutor)]()
        var lastResult: Option[ScalaResolveResult] = None
        while (candidatesSeq.nonEmpty) {
          val (next, rest) = mostSpecific.nextLayerSpecificForImplicitParameters(lastResult, candidatesSeq)
          next match {
            case Some(c) =>
              candidatesSeq = rest
              forMap(c, withLocalTypeInference, checkFast = false) match {
                case Some(res) if res._1.isApplicable()  =>
                  predicate match {
                    case Some(fun) if withLocalTypeInference =>
                      fun(res._1, res._2) match {
                        case Some(newRes) =>
                          lastResult = Some(c)
                          results += newRes
                        case _ => lastResult = None
                      }
                    case _ =>
                      lastResult = Some(c)
                      results += res
                  }
                case _ => lastResult = None
              }
            case None => candidatesSeq = Seq.empty
          }
        }
        results.toSet
      }

      if (fullInfo)
        return (candidates.toSeq.map(c => forMap(c, withLocalTypeInference = false, checkFast = false)) ++
          candidates.toSeq.map(c => forMap(c, withLocalTypeInference = true, checkFast = false))).
          flatMap(_.toSeq).map(_._1).toSet

      var applicable = mapCandidates(withLocalTypeInference = false)
      if (applicable.isEmpty) applicable = mapCandidates(withLocalTypeInference = true)

      //todo: remove it when you will be sure, that filtering according to implicit parameters works ok
      val filtered = applicable.filter {
        case (res: ScalaResolveResult, subst: ScSubstitutor) =>
          res.problems match {
            case Seq(WrongTypeParameterInferred) => false
            case _ => true
          }
      }
      val actuals =
        if (filtered.isEmpty) applicable
        else filtered

      mostSpecific.mostSpecificForImplicitParameters(actuals) match {
        case Some(r) => HashSet(r)
        case _ => applicable.map(_._1)
      }
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
                  case Some(AliasType(`ta`, _, _)) => (true, types.Any)
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
        case tp@ScTypeVariable(name) => wilds.find(_.name == name).map(w => (true, w.upperBound)).getOrElse((false, tp))
        case tp@ScDesignatorType(element) => element match {
          case a: ScTypeAlias if a.getContext.isInstanceOf[ScExistentialClause] =>
            wilds.find(_.name == a.name).map(w => (true, w.upperBound)).getOrElse((false, tp))
          case _ => (false, tp)
        }
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
      case ScParameterizedType(designator, _) => Set(designator)
      case tp@ScDesignatorType(o: ScObject) => Set(tp)
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
      case ScParameterizedType(des, args) => 1 + args.foldLeft(0)(_ + complexity(_))
      case ScDesignatorType(o: ScObject) => 1
      case ScDesignatorType(v: ScTypedDefinition) =>
        val valueType: ScType = v.getType(TypingContext.empty).getOrAny
        1 + complexity(valueType)
      case ScCompoundType(comps, _, _) => comps.foldLeft(0)(_ + complexity(_))
      case _ => 1
    }
  }
}