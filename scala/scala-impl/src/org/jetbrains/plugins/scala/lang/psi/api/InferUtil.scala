package org.jetbrains.plugins.scala
package lang.psi.api

import com.intellij.openapi.diagnostic.Logger
import com.intellij.psi.PsiElement
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.macros.evaluator.{MacroContext, ScalaMacroEvaluator}
import org.jetbrains.plugins.scala.lang.psi.api.base.patterns.ScBindingPattern
import org.jetbrains.plugins.scala.lang.psi.api.base.{ScFieldId, ScLiteral}
import org.jetbrains.plugins.scala.lang.psi.api.expr.{MethodInvocation, ScExpression}
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScFunction
import org.jetbrains.plugins.scala.lang.psi.api.statements.params.{ScParameter, ScTypeParam, TypeParamIdOwner}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScObject
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.{ScNamedElement, ScTypedDefinition}
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementFactory.{createExpressionWithContextFromText, createParameterFromText}
import org.jetbrains.plugins.scala.lang.psi.implicits.ImplicitCollector.ImplicitState
import org.jetbrains.plugins.scala.lang.psi.implicits.{ImplicitCollector, ImplicitsRecursionGuard}
import org.jetbrains.plugins.scala.lang.psi.types.Compatibility.{ConformanceExtResult, Expression}
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
import org.jetbrains.plugins.scala.util.SAMUtil

import scala.annotation.tailrec
import scala.collection.Seq
import scala.collection.mutable.ArrayBuffer
import scala.util.control.ControlThrowable

/**
  * @author Alexander Podkhalyuzin
  */

object InferUtil {
  val notFoundParameterName = "NotFoundParameter239239239"

  val tagsAndManifists = Set(
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

  private val LOG = Logger.getInstance("#org.jetbrains.plugins.scala.lang.psi.api.InferUtil$")

  private def isDebugImplicitParameters = LOG.isDebugEnabled

  def logInfo(searchLevel: Int, message: => String) {
    val indent = Seq.fill(searchLevel)("  ").mkString
    //    println(indent + message)
    if (isDebugImplicitParameters) {
      LOG.debug(indent + message)
    }
  }

  /**
    * This method can find implicit parameters for given MethodType
    *
    * @param res     MethodType or PolymorphicType(MethodType)
    * @param element place to find implicit parameters
    * @param canThrowSCE   if true can throw SafeCheckException if it not found not ambiguous implicit parameters
    * @return updated type and sequence of implicit parameters
    */
  def updateTypeWithImplicitParameters(res: ScType, element: PsiElement, coreElement: Option[ScNamedElement], canThrowSCE: Boolean,
                                       searchImplicitsRecursively: Int = 0, fullInfo: Boolean): (ScType, Option[Seq[ScalaResolveResult]]) = {
    implicit val ctx: ProjectContext = element

    var resInner = res
    var implicitParameters: Option[Seq[ScalaResolveResult]] = None
    res match {
      case t@ScTypePolymorphicType(mt@ScMethodType(retType, _, isImplicit), _) if !isImplicit =>
        // See SCL-3516
        val (updatedType, ps) =
          updateTypeWithImplicitParameters(t.copy(internalType = retType), element, coreElement, canThrowSCE, fullInfo = fullInfo)
        implicitParameters = ps
        implicit val elementScope: ElementScope = mt.elementScope

        updatedType match {
          case tpt: ScTypePolymorphicType =>
            //don't lose information from type parameters of res, updated type may some of type parameters removed
            val abstractSubst = t.abstractOrLowerTypeSubstitutor
            val mtWithoutImplicits = mt.copy(result = tpt.internalType)
            resInner = t.copy(internalType = abstractSubst(mtWithoutImplicits),
              typeParameters = tpt.typeParameters)
          case _ => //shouldn't be there
            resInner = t.copy(internalType = mt.copy(result = updatedType))
        }
      case ScTypePolymorphicType(mt@ScMethodType(retType, params, isImplicit), typeParams) if isImplicit =>
        implicit val elementScope: ElementScope = mt.elementScope

        val splitMethodType = params.reverse.foldLeft(retType) {
          case (tp: ScType, param: Parameter) => ScMethodType(tp, Seq(param), isImplicit = true)
        }

        resInner = ScTypePolymorphicType(splitMethodType, typeParams)
        val paramsForInferBuffer = new ArrayBuffer[Parameter]()
        val exprsBuffer          = new ArrayBuffer[Compatibility.Expression]()
        val resolveResultsBuffer = new ArrayBuffer[ScalaResolveResult]()

        //todo: do we need to execute this loop several times?
        var i = 0
        while (i < params.size) {
          i += 1
          resInner match {
            case t@ScTypePolymorphicType(ScMethodType(retTypeSingle, paramsSingle, _), typeParamsSingle) =>
              val abstractSubstitutor = t.abstractOrLowerTypeSubstitutor

              val (paramsForInfer, exprs, resolveResults) =
                findImplicits(paramsSingle, coreElement, element, canThrowSCE, searchImplicitsRecursively, abstractSubstitutor)

              resInner = localTypeInference(retTypeSingle, paramsForInfer, exprs, typeParamsSingle,
                canThrowSCE = canThrowSCE || fullInfo)

              paramsForInferBuffer ++= paramsForInfer
              exprsBuffer          ++= exprs
              resolveResultsBuffer ++= resolveResults
          }
        }

        implicitParameters = Some(resolveResultsBuffer)

        val dependentSubst = ScSubstitutor.paramToExprType(paramsForInferBuffer, exprsBuffer)
        resInner = dependentSubst(resInner)
      case mt@ScMethodType(retType, _, isImplicit) if !isImplicit =>
        // See SCL-3516
        val (updatedType, ps) = updateTypeWithImplicitParameters(retType, element, coreElement, canThrowSCE, fullInfo = fullInfo)
        implicitParameters = ps
        implicit val elementScope: ElementScope = mt.elementScope

        resInner = mt.copy(result = updatedType)
      case ScMethodType(retType, params, isImplicit) if isImplicit =>
        val (paramsForInfer, exprs, resolveResults) =
          findImplicits(params, coreElement, element, canThrowSCE, searchImplicitsRecursively)

        implicitParameters = Some(resolveResults)
        resInner = retType
        val dependentSubst = ScSubstitutor.paramToExprType(paramsForInfer, exprs)
        resInner = dependentSubst(resInner)
      case _ =>
    }
    (resInner, implicitParameters)
  }


  def findImplicits(params: Seq[Parameter], coreElement: Option[ScNamedElement], place: PsiElement,
                    canThrowSCE: Boolean, searchImplicitsRecursively: Int = 0,
                    abstractSubstitutor: ScSubstitutor = ScSubstitutor.empty
                   ): (Seq[Parameter], Seq[Compatibility.Expression], Seq[ScalaResolveResult]) = {

    implicit val project: ProjectContext = place.getProject

    val exprs = new ArrayBuffer[Expression]
    val paramsForInfer = new ArrayBuffer[Parameter]()
    val resolveResults = new ArrayBuffer[ScalaResolveResult]
    val iterator = params.iterator
    while (iterator.hasNext) {
      val param = iterator.next()
      val paramType = abstractSubstitutor(param.paramType) //we should do all of this with information known before
      val implicitState = ImplicitState(place, paramType, paramType, coreElement, isImplicitConversion = false,
          searchImplicitsRecursively, None, fullInfo = false, Some(ImplicitsRecursionGuard.currentMap))
      val collector = new ImplicitCollector(implicitState)
      val results = collector.collect()
      if (results.length == 1) {
        if (canThrowSCE && !results.head.isApplicable()) throw new SafeCheckException
        resolveResults += results.head

        def updateExpr(): Unit = {
          val maybeType = results.headOption
            .flatMap(extractImplicitParameterType)

          exprs ++= maybeType.map(new Expression(_))
        }
        val evaluator = ScalaMacroEvaluator.getInstance(project)
        evaluator.checkMacro(results.head.getElement, MacroContext(place, Some(paramType))) match {
          case Some(tp) => exprs += new Expression(tp)
          case None => updateExpr()
        }
        paramsForInfer += param
      } else {
        val compilerGenerated = compilerGeneratedInstance(paramType)
        val result = compilerGenerated.getOrElse {
          if (param.isDefault && param.paramInCode.nonEmpty) {
            //todo: should be added for infer to
            //todo: what if paramInCode is null?
            new ScalaResolveResult(param.paramInCode.get)
          }
          else if (canThrowSCE) throw new SafeCheckException
          else {
            val problem =
              if (results.isEmpty) NotFoundImplicitParameter(paramType)
              else AmbiguousImplicitParameters(results)

            val psiParam = param.paramInCode.getOrElse {
              val text = param.name + " : Int"
              createParameterFromText(text)(place.getManager)
            }

            new ScalaResolveResult(psiParam, problems = Seq(problem), implicitSearchState = Some(implicitState))
          }
        }
        resolveResults += result
      }
    }
    (paramsForInfer, exprs, resolveResults)
  }

  private def compilerGeneratedInstance(tp: ScType): Option[ScalaResolveResult] = tp match {
    case p@ParameterizedType(des, params) =>
      des.extractClass.collect {
        case clazz if areEligible(params, clazz.qualifiedName) => new ScalaResolveResult(clazz, p.substitutor)
      }
    case _ => None
  }


  private def areEligible(params: Seq[ScType], typeFqn: String): Boolean =
    (typeFqn, params) match {
      case (ValueOf, Seq(t))              => eligibleForValueOf(t)
      case (ConformsWitness, Seq(t1, t2)) => t1.conforms(t2)
      case (EquivWitness, Seq(t1, t2))    => t1.equiv(t2)
      case _ if params.size == 1          => tagsAndManifists.contains(typeFqn)
      case _                              => false
    }

  private def eligibleForValueOf(t: ScType): Boolean = {
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
    * Util method to update type according to expected type
    *
    * @param _nonValueType          type, to update it should be PolymorphicType
    * @param expectedType           appropriate expected type
    * @param expr                   place
    * @param canThrowSCE            we fail to get right type then if canThrowSCE throw SafeCheckException
    * @return updated type
    */
  def updateAccordingToExpectedType(_nonValueType: ScType,
                                    filterTypeParams: Boolean,
                                    expectedType: Option[ScType],
                                    expr: PsiElement,
                                    canThrowSCE: Boolean): ScType = {
    implicit val ctx: ProjectContext = expr
    val Unit = ctx.stdTypes.Unit

    @tailrec
    def shouldSearchImplicit(t: ScType, first: Boolean = true): Boolean = t match {
      case ScMethodType(_, _, isImplicit) if isImplicit => !first  // implicit method type on top level means explicit implicit argument
      case ScTypePolymorphicType(internalType, _)       => shouldSearchImplicit(internalType, first = first)
      case ScMethodType(returnType, _, _)               => shouldSearchImplicit(returnType, first = false)
      case _                                            => false
    }

    def implicitSearchFails(tp: ScType): Boolean = expr match {
      case e: ScExpression =>
        val implicitArgs = e.updatedWithImplicitParameters(tp, checkExpectedType = false)._2.toSeq.flatten
        implicitArgs.exists {
          case srr if srr.isNotFoundImplicitParameter  => true
          case srr if srr.isAmbiguousImplicitParameter =>
            // we found several implicits, but not all type parameters are fully inferred yet, it may be fine
            tp.asOptionOf[ScTypePolymorphicType].exists(_.typeParameters.isEmpty)
          case _                                       => false
        }
      case _ => false
    }

    def cantFindImplicitsFor(tp: ScType): Boolean = shouldSearchImplicit(tp) && implicitSearchFails(tp)

    def doLocalTypeInference(tpt: ScTypePolymorphicType, expected: ScType): ScType = {
      val ScTypePolymorphicType(internal, typeParams) = tpt

      val sameDepth = internal match {
        case m: ScMethodType => truncateMethodType(m, expr)
        case _               => internal
      }

      val valueType = sameDepth.inferValueType

      val expectedParam = Parameter("", None, expected, expected)
      val expressionToUpdate = new Expression(ScSubstitutor.bind(typeParams)(UndefinedType(_)).apply(valueType))

      val inferredWithExpected =
        localTypeInference(internal, Seq(expectedParam), Seq(expressionToUpdate), typeParams,
          shouldUndefineParameters = false,
          canThrowSCE = canThrowSCE,
          filterTypeParams = filterTypeParams)

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
      if (cantFindImplicitsFor(result)) _nonValueType
      else                              result
    }

    val nonValueType = (_nonValueType, expectedType) match {
      case (tpt: ScTypePolymorphicType, Some(expected)) if !expected.equiv(Unit) => doLocalTypeInference(tpt, expected)
      case _                                                                     => _nonValueType
    }

    if (!expr.isInstanceOf[ScExpression]) return nonValueType

    // interim fix for SCL-3905.
    def applyImplicitViewToResult(mt: ScMethodType, expectedType: Option[ScType], fromSAM: Boolean = false,
                                  fromMethodInvocation: Boolean = false): ScMethodType = {
      implicit val elementScope: ElementScope = mt.elementScope
      expr match {
        case _: MethodInvocation if !fromMethodInvocation =>
          mt.result match {
            case methodType: ScMethodType => mt.copy(
              result = applyImplicitViewToResult(methodType, expectedType, fromSAM, fromMethodInvocation = true)
            )
            case _ => mt
          }
        case _ =>
          expectedType match {
            case Some(expected) if mt.result.conforms(expected) => mt
            case Some(FunctionType(expectedRet, expectedParams)) if expectedParams.length == mt.params.length =>
              if (expectedRet.equiv(Unit)) { //value discarding
                ScMethodType(Unit, mt.params, mt.isImplicit)
              } else {
                mt.result match {
                  case methodType: ScMethodType => return mt.copy(
                    result = applyImplicitViewToResult(methodType, Some(expectedRet), fromSAM))
                  case _ =>
                }
                val dummyExpr = createExpressionWithContextFromText("null", expr.getContext, expr)
                dummyExpr.asInstanceOf[ScLiteral].setTypeForNullWithoutImplicits(Some(mt.result))
                val updatedResultType = dummyExpr.getTypeAfterImplicitConversion(expectedOption = Some(expectedRet))

                expr.asInstanceOf[ScExpression].setAdditionalExpression(Some(dummyExpr, expectedRet))

                ScMethodType(updatedResultType.tr.getOrElse(mt.result), mt.params, mt.isImplicit)
              }
            case Some(tp) if !fromSAM && expr.isSAMEnabled &&
              (mt.params.nonEmpty || expr.scalaLanguageLevelOrDefault == ScalaLanguageLevel.Scala_2_11) =>
              //we do this to update additional expression, so that implicits work correctly
              //@see SingleAbstractMethodTest.testEtaExpansionImplicit
              val requiredSAMType = SAMUtil.toSAMType(tp, expr)
              applyImplicitViewToResult(mt, requiredSAMType, fromSAM = true)
            case _ => mt
          }
      }
    }

    nonValueType match {
      case tpt@ScTypePolymorphicType(mt: ScMethodType, _) =>
        val canConform = if (!filterTypeParams) {
          val subst         = tpt.abstractTypeSubstitutor
          val withAbstracts = subst(mt).asInstanceOf[ScMethodType]
          withAbstracts.result
        } else mt.result

        if (expectedType.forall(canConform.conforms)) tpt
        else tpt.copy(internalType = applyImplicitViewToResult(mt, expectedType))
      case mt: ScMethodType =>
        applyImplicitViewToResult(mt, expectedType)
      case t => t
    }
  }

  //truncate method type to have a chance to conform to expected
  private[this] def truncateMethodType(tpe: ScType, expr: PsiElement): ScType = {
    def withoutImplicitClause(internal: ScType): ScType = {
      internal match {
        case ScMethodType(retType, _, true) => retType
        case m @ ScMethodType(retType, params, false) =>
          ScMethodType(withoutImplicitClause(retType), params, isImplicit = false)(m.elementScope)
        case other => other
      }
    }

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
      case inv: MethodInvocation => removeNComponents(withoutImplicits, countParameterLists(inv))
      case _                     => withoutImplicits
    }
  }

  def extractImplicitParameterType(result: ScalaResolveResult): Option[ScType] =
    result.implicitParameterType.orElse {
      val ScalaResolveResult(element, substitutor) = result

      val maybeType = element match {
        case _: ScObject |
             _: ScParameter |
             _: ScBindingPattern |
             _: ScFieldId => element.asInstanceOf[Typeable].`type`().toOption
        case function: ScFunction => functionTypeNoImplicits(function)
      }

      maybeType.map(substitutor)
    }

  def localTypeInference(retType: ScType, params: Seq[Parameter], exprs: Seq[Expression],
                         typeParams: Seq[TypeParameter],
                         shouldUndefineParameters: Boolean = true,
                         canThrowSCE: Boolean = false,
                         filterTypeParams: Boolean = true): ScTypePolymorphicType = localTypeInferenceWithApplicabilityExt(
    retType, params, exprs, typeParams, shouldUndefineParameters, canThrowSCE, filterTypeParams
  )._1

  class SafeCheckException extends ControlThrowable

  def localTypeInferenceWithApplicabilityExt(retType: ScType, params: Seq[Parameter], exprs: Seq[Expression],
                                             typeParams: Seq[TypeParameter],
                                             shouldUndefineParameters: Boolean = true,
                                             canThrowSCE: Boolean = false,
                                             filterTypeParams: Boolean = true
                                            ): (ScTypePolymorphicType, ConformanceExtResult) = {
    implicit val projectContext: ProjectContext = retType.projectContext

    val typeParamIds = typeParams.map(_.typeParamId).toSet
    def hasRecursiveTypeParams(typez: ScType): Boolean = typez.hasRecursiveTypeParameters(typeParamIds)

    // See SCL-3052, SCL-3058
    // This corresponds to use of `isCompatible` in `Infer#methTypeArgs` in scalac, where `isCompatible` uses `weak_<:<`
    val s: ScSubstitutor = if (shouldUndefineParameters) ScSubstitutor.bind(typeParams)(UndefinedType(_)) else ScSubstitutor.empty
    val abstractSubst = ScTypePolymorphicType(retType, typeParams).abstractTypeSubstitutor
    val paramsWithUndefTypes = params.map(p => p.copy(paramType = s(p.paramType),
      expectedType = abstractSubst(p.paramType), defaultType = p.defaultType.map(s)))
    val conformanceResult@ConformanceExtResult(problems, constraints, _, _) =
      Compatibility.checkConformanceExt(checkNames = true, paramsWithUndefTypes, exprs, checkWithImplicits = true,
      isShapesResolve = false)

    val tpe = if (problems.isEmpty) {
      constraints.substitutionBounds(canThrowSCE) match {
        case Some(bounds@SubstitutionBounds(_, lowerMap, upperMap)) =>
          val unSubst = bounds.substitutor
          if (!filterTypeParams) {

            def combineBounds(tp: TypeParameter, isLower: Boolean): ScType = {
              val bound = if (isLower) tp.lowerType else tp.upperType
              val substedBound = unSubst(bound)
              val boundsMap = if (isLower) lowerMap else upperMap
              val combine: (ScType, ScType) => ScType = if (isLower) _ lub _ else _ glb _

              boundsMap.get(tp.typeParamId) match {
                case Some(fromMap) =>
                  val mayCombine = !substedBound.equiv(fromMap) && !hasRecursiveTypeParams(substedBound)

                  if (mayCombine) combine(substedBound, fromMap)
                  else            fromMap
                case _ => substedBound
              }
            }

            val undefiningSubstitutor = ScSubstitutor.bind(typeParams)(UndefinedType(_))
            ScTypePolymorphicType(retType, typeParams.map { tp =>
              val lower = combineBounds(tp, isLower = true)
              val upper = combineBounds(tp, isLower = false)

              if (canThrowSCE && !undefiningSubstitutor(lower).weakConforms(undefiningSubstitutor(upper)))
                throw new SafeCheckException

              TypeParameter(tp.psiTypeParameter, /* doesn't important here */
                tp.typeParameters,
                lower,
                upper)
            })
          } else {

            def addConstraints(un: ConstraintSystem, tp: TypeParameter): ConstraintSystem = {
              val typeParamId = tp.typeParamId
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
              case ConstraintSystem(substitutor) => updateWithSubst(substitutor)
              case _ if !canThrowSCE             => updateWithSubst(unSubst)
              case _                             => throw new SafeCheckException
            }
          }
        case None => throw new SafeCheckException
      }
    } else ScTypePolymorphicType(retType, typeParams)
    (tpe, conformanceResult)
  }

  def functionTypeNoImplicits(function: ScFunction): Option[ScType] = {
    val retType = function.returnType.toOption

    collectReverseParamTypesNoImplicits(function) match {
      case Some(params) =>
        implicit val scope: ElementScope = ElementScope(function)
        retType.map(params.foldLeft(_)((res, params) => FunctionType(res, params)))
      case None => None
    }
  }

  private def collectReverseParamTypesNoImplicits(function: ScFunction): Option[Seq[Seq[ScType]]] = {
    val buffer = ArrayBuffer.empty[Seq[ScType]]
    val clauses = function.paramClauses.clauses

    //for performance
    var idx = clauses.length - 1
    while (idx >= 0) {
      val cl = clauses(idx)
      if (!cl.isImplicit) {
        val parameters = cl.parameters
        val paramTypes = parameters.flatMap(_.`type`().toOption)

        if (paramTypes.size != parameters.size) return None
        else buffer += paramTypes
      }
      idx -= 1
    }
    Some(buffer)
  }
}
