package org.jetbrains.plugins.scala
package lang.psi.api

import com.intellij.openapi.diagnostic.Logger
import com.intellij.psi.{PsiElement, PsiNamedElement, PsiTypeParameterListOwner}
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.macros.evaluator.{MacroContext, ScalaMacroEvaluator}
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiUtil
import org.jetbrains.plugins.scala.lang.psi.api.base.patterns.ScBindingPattern
import org.jetbrains.plugins.scala.lang.psi.api.base.{ScFieldId, ScLiteral}
import org.jetbrains.plugins.scala.lang.psi.api.expr.{MethodInvocation, ScExpression}
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScFunction
import org.jetbrains.plugins.scala.lang.psi.api.statements.params.{ScParameter, ScTypeParam}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScObject
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.{ScNamedElement, ScTypeParametersOwner}
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementFactory.{createExpressionWithContextFromText, createParameterFromText}
import org.jetbrains.plugins.scala.lang.psi.implicits.ImplicitCollector.ImplicitState
import org.jetbrains.plugins.scala.lang.psi.implicits.{ImplicitCollector, ImplicitsRecursionGuard}
import org.jetbrains.plugins.scala.lang.psi.types.Compatibility.Expression
import org.jetbrains.plugins.scala.lang.psi.types._
import org.jetbrains.plugins.scala.lang.psi.types.api._
import org.jetbrains.plugins.scala.lang.psi.types.nonvalue.{Parameter, ScMethodType, ScTypePolymorphicType}
import org.jetbrains.plugins.scala.lang.psi.types.result.{Success, TypeResult, Typeable}
import org.jetbrains.plugins.scala.lang.resolve.ScalaResolveResult
import org.jetbrains.plugins.scala.project.ScalaLanguageLevel.Scala_2_10
import org.jetbrains.plugins.scala.project._

import scala.collection.Seq
import scala.collection.mutable.ArrayBuffer
import scala.util.control.ControlThrowable

/**
  * @author Alexander Podkhalyuzin
  */

object InferUtil {
  val notFoundParameterName = "NotFoundParameter239239239"
  val skipQualSet = Set("scala.reflect.ClassManifest", "scala.reflect.Manifest",
    "scala.reflect.ClassTag", "scala.reflect.api.TypeTags.TypeTag",
    "scala.reflect.api.TypeTags.WeakTypeTag")
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
    * @param check   if true can throw SafeCheckException if it not found not ambiguous implicit parameters
    * @return updated type and sequence of implicit parameters
    */
  def updateTypeWithImplicitParameters(res: ScType, element: PsiElement, coreElement: Option[ScNamedElement], check: Boolean,
                                       searchImplicitsRecursively: Int = 0, fullInfo: Boolean): (ScType, Option[Seq[ScalaResolveResult]]) = {
    implicit val ctx: ProjectContext = element

    var resInner = res
    var implicitParameters: Option[Seq[ScalaResolveResult]] = None
    res match {
      case t@ScTypePolymorphicType(mt@ScMethodType(retType, _, impl), _) if !impl =>
        // See SCL-3516
        val (updatedType, ps) =
          updateTypeWithImplicitParameters(t.copy(internalType = retType), element, coreElement, check, fullInfo = fullInfo)
        implicitParameters = ps
        implicit val elementScope = mt.elementScope

        updatedType match {
          case tpt: ScTypePolymorphicType =>
            resInner = t.copy(internalType = mt.copy(returnType = tpt.internalType),
              typeParameters = tpt.typeParameters)
          case _ => //shouldn't be there
            resInner = t.copy(internalType = mt.copy(returnType = updatedType))
        }
      case t@ScTypePolymorphicType(mt@ScMethodType(retType, params, impl), typeParams) if impl =>
        val fullAbstractSubstitutor = t.abstractOrLowerTypeSubstitutor
        val coreTypes = params.map(p => fullAbstractSubstitutor.subst(p.paramType))
        implicit val elementScope = mt.elementScope

        val splitMethodType = params.foldRight(retType) {
          (param: Parameter, tp: ScType) => ScMethodType(tp, Seq(param), isImplicit = true)
        }
        resInner = ScTypePolymorphicType(splitMethodType, typeParams)
        val paramsForInferBuffer = new ArrayBuffer[Parameter]()
        val exprsBuffer = new ArrayBuffer[Compatibility.Expression]()
        val resolveResultsBuffer = new ArrayBuffer[ScalaResolveResult]()

        //todo: do we need to execute this loop several times?
        var i = 0
        while (i < coreTypes.size) {
          i += 1
          resInner match {
            case t@ScTypePolymorphicType(ScMethodType(retTypeSingle, paramsSingle, _), typeParamsSingle) =>
              val polymorphicSubst = t.polymorphicTypeSubstitutor
              val abstractSubstitutor: ScSubstitutor = t.abstractOrLowerTypeSubstitutor
              val (paramsForInfer, exprs, resolveResults) =
                findImplicits(paramsSingle, coreElement, element, check, searchImplicitsRecursively, abstractSubstitutor, polymorphicSubst)
              resInner = localTypeInference(retTypeSingle, paramsForInfer, exprs, typeParamsSingle,
                safeCheck = check || fullInfo)
              paramsForInferBuffer ++= paramsForInfer
              exprsBuffer ++= exprs
              resolveResultsBuffer ++= resolveResults
          }
        }

        implicitParameters = Some(resolveResultsBuffer)
        val dependentSubst = ScSubstitutor(() => {
          val level = element.scalaLanguageLevelOrDefault
          if (level >= Scala_2_10) {
            paramsForInferBuffer.zip(exprsBuffer).map {
              case (param: Parameter, expr: Expression) =>
                val paramType: ScType = expr.getTypeAfterImplicitConversion(checkImplicits = true,
                  isShape = false, Some(param.expectedType))._1.getOrAny
                (param, paramType)
            }.toMap
          } else Map.empty
        })
        resInner = dependentSubst.subst(resInner)
      case mt@ScMethodType(retType, _, isImplicit) if !isImplicit =>
        // See SCL-3516
        val (updatedType, ps) = updateTypeWithImplicitParameters(retType, element, coreElement, check, fullInfo = fullInfo)
        implicitParameters = ps
        implicit val elementScope = mt.elementScope

        resInner = mt.copy(returnType = updatedType)
      case ScMethodType(retType, params, isImplicit) if isImplicit =>
        val (paramsForInfer, exprs, resolveResults) =
          findImplicits(params, coreElement, element, check, searchImplicitsRecursively)

        implicitParameters = Some(resolveResults)
        resInner = retType
        val dependentSubst = ScSubstitutor(() => {
          val level = element.scalaLanguageLevelOrDefault
          if (level >= Scala_2_10) {
            paramsForInfer.zip(exprs).map {
              case (param: Parameter, expr: Expression) =>
                val paramType: ScType = expr.getTypeAfterImplicitConversion(checkImplicits = true,
                  isShape = false, Some(param.expectedType))._1.getOrAny
                (param, paramType)
            }.toMap
          } else Map.empty
        })
        resInner = dependentSubst.subst(resInner)
      case _ =>
    }
    (resInner, implicitParameters)
  }


  def findImplicits(params: Seq[Parameter], coreElement: Option[ScNamedElement], place: PsiElement,
                    check: Boolean, searchImplicitsRecursively: Int = 0,
                    abstractSubstitutor: ScSubstitutor = ScSubstitutor.empty,
                    polymorphicSubst: ScSubstitutor = ScSubstitutor.empty
                   ): (Seq[Parameter], Seq[Compatibility.Expression], Seq[ScalaResolveResult]) = {

    implicit val project = place.getProject

    val exprs = new ArrayBuffer[Expression]
    val paramsForInfer = new ArrayBuffer[Parameter]()
    val resolveResults = new ArrayBuffer[ScalaResolveResult]
    val iterator = params.iterator
    while (iterator.hasNext) {
      val param = iterator.next()
      val paramType = abstractSubstitutor.subst(param.paramType) //we should do all of this with information known before
      val implicitState = ImplicitState(place, paramType, paramType, coreElement, isImplicitConversion = false,
          searchImplicitsRecursively, None, fullInfo = false, Some(ImplicitsRecursionGuard.currentMap))
      val collector = new ImplicitCollector(implicitState)
      val results = collector.collect()
      if (results.length == 1) {
        if (check && !results.head.isApplicable()) throw new SafeCheckException
        resolveResults += results.head

        def updateExpr(): Unit = {
          val maybeType = results.headOption
            .flatMap(extractImplicitParameterType)
            .map(polymorphicSubst.subst)

          exprs ++= maybeType.map(new Expression(_))
        }
        val evaluator = ScalaMacroEvaluator.getInstance(project)
        evaluator.isMacro(results.head.getElement) match {
          case Some(m) =>
            evaluator.checkMacro(m, MacroContext(place, Some(paramType))) match {
              case Some(tp) => exprs += new Expression(polymorphicSubst subst tp)
              case None => updateExpr()
            }
          case _ => updateExpr()
        }
        paramsForInfer += param
      } else {
        def checkManifest(fun: ScalaResolveResult => Unit) {
          val result = paramType match {
            case p@ParameterizedType(des, Seq(_)) =>
              des.extractClass match {
                case Some(clazz) if skipQualSet.contains(clazz.qualifiedName) =>
                  //do not throw, it's safe
                  new ScalaResolveResult(clazz, p.substitutor)
                case _ => null
              }
            case _ => null
          }
          fun(result)
        }
        //check if it's ClassManifest parameter:
        checkManifest(r => {
          if (r == null && param.isDefault && param.paramInCode.nonEmpty) {
            //todo: should be added for infer to
            //todo: what if paramInCode is null?
            resolveResults += new ScalaResolveResult(param.paramInCode.get)
          } else if (r == null && check) throw new SafeCheckException
          else if (r == null) {
            val parameter = createParameterFromText(s"$notFoundParameterName: Int")(place.getManager)
            resolveResults += new ScalaResolveResult(parameter, implicitSearchState = Some(implicitState))
          } else resolveResults += r
        })
      }
    }
    (paramsForInfer, exprs, resolveResults)
  }

  /**
    * Util method to update type according to expected type
    *
    * @param _nonValueType          type, to update it should be PolymorphicType(MethodType)
    * @param fromImplicitParameters we shouldn't update if it's anonymous function
    *                               also we can update just for simple type without function
    * @param expectedType           appropriate expected type
    * @param expr                   place
    * @param check                  we fail to get right type then if check throw SafeCheckException
    * @return updated type
    */
  def updateAccordingToExpectedType(_nonValueType: TypeResult[ScType],
                                    fromImplicitParameters: Boolean,
                                    filterTypeParams: Boolean,
                                    expectedType: Option[ScType], expr: PsiElement,
                                    check: Boolean): TypeResult[ScType] = {
    implicit val ctx: ProjectContext = expr
    val Unit = ctx.stdTypes.Unit

    var nonValueType = _nonValueType
    nonValueType match {
      case Success(ScTypePolymorphicType(m@ScMethodType(internal, _, impl), typeParams), _)
        if expectedType.isDefined && (!fromImplicitParameters || impl) =>
        def updateRes(expected: ScType) {
          if (expected.equiv(Unit)) return //do not update according to Unit type
          val innerInternal = internal match {
              case ScMethodType(inter, _, innerImpl) if innerImpl && !fromImplicitParameters => inter
              case _ => internal
            }
          val valueType = (expr match {
            case scExpr: ScExpression =>
              scExpr.updatedWithImplicitParameters(innerInternal, checkExpectedType = true)._1
            case _ => innerInternal
          }).inferValueType
          val update: ScTypePolymorphicType = localTypeInference(m,
            Seq(Parameter("", None, expected, expected, isDefault = false, isRepeated = false, isByName = false)),
            Seq(new Expression(undefineSubstitutor(typeParams).subst(valueType))),
            typeParams, shouldUndefineParameters = false, safeCheck = check, filterTypeParams = filterTypeParams)
          nonValueType = Success(update, Some(expr)) //here should work in different way:
        }
        updateRes(expectedType.get)
      //todo: Something should be unified, that's bad to have fromImplicitParameters parameter.
      case Success(ScTypePolymorphicType(internal, typeParams), _) if expectedType.isDefined && fromImplicitParameters =>
        def updateRes(expected: ScType) {
          nonValueType = Success(localTypeInference(internal,
            Seq(Parameter("", None, expected, expected, isDefault = false, isRepeated = false, isByName = false)),
            Seq(new Expression(undefineSubstitutor(typeParams).subst(internal.inferValueType))),
            typeParams, shouldUndefineParameters = false, safeCheck = check,
            filterTypeParams = filterTypeParams), Some(expr)) //here should work in different way:
        }
        updateRes(expectedType.get)
      case _ =>
    }

    if (!expr.isInstanceOf[ScExpression]) return nonValueType

    // interim fix for SCL-3905.
    def applyImplicitViewToResult(mt: ScMethodType, expectedType: Option[ScType], fromSAM: Boolean = false,
                                  fromMethodInvocation: Boolean = false): ScMethodType = {
      implicit val elementScope = mt.elementScope
      expr match {
        case _: MethodInvocation if !fromMethodInvocation =>
          mt.returnType match {
            case methodType: ScMethodType => mt.copy(
              returnType = applyImplicitViewToResult(methodType, expectedType, fromSAM, fromMethodInvocation = true)
            )
            case _ => mt
          }
        case _ =>
          expectedType match {
            case Some(expected) if mt.returnType.conforms(expected) => mt
            case Some(FunctionType(expectedRet, expectedParams)) if expectedParams.length == mt.params.length =>
              if (expectedRet.equiv(Unit)) { //value discarding
                ScMethodType(Unit, mt.params, mt.isImplicit)
              } else {
                mt.returnType match {
                  case methodType: ScMethodType => return mt.copy(
                    returnType = applyImplicitViewToResult(methodType, Some(expectedRet), fromSAM))
                  case _ =>
                }
                val dummyExpr = createExpressionWithContextFromText("null", expr.getContext, expr)
                dummyExpr.asInstanceOf[ScLiteral].setTypeForNullWithoutImplicits(Some(mt.returnType))
                val updatedResultType = dummyExpr.getTypeAfterImplicitConversion(expectedOption = Some(expectedRet))

                expr.asInstanceOf[ScExpression].setAdditionalExpression(Some(dummyExpr, expectedRet))

                ScMethodType(updatedResultType.tr.getOrElse(mt.returnType), mt.params, mt.isImplicit)
              }
            case Some(tp) if !fromSAM && ScalaPsiUtil.isSAMEnabled(expr) &&
              (mt.params.nonEmpty || expr.scalaLanguageLevelOrDefault == ScalaLanguageLevel.Scala_2_11) =>
              //we do this to update additional expression, so that implicits work correctly
              //@see SingleAbstractMethodTest.testEtaExpansionImplicit
              val requiredSAMType = ScalaPsiUtil.toSAMType(tp, expr)
              applyImplicitViewToResult(mt, requiredSAMType, fromSAM = true)
            case _ => mt
          }
      }
    }

    nonValueType match {
      case Success(tpt@ScTypePolymorphicType(mt: ScMethodType, _), elem) =>
        Success(tpt.copy(internalType = applyImplicitViewToResult(mt, expectedType)), elem)
      case Success(mt: ScMethodType, elem) =>
        Success(applyImplicitViewToResult(mt, expectedType), elem)
      case tr => tr
    }
  }

  def extractImplicitParameterType(result: ScalaResolveResult): Option[ScType] =
    result.implicitParameterType.orElse {
      val ScalaResolveResult(element, substitutor) = result

      val maybeType = element match {
        case _: ScObject |
             _: ScParameter |
             _: ScBindingPattern |
             _: ScFieldId => element.asInstanceOf[Typeable].getType().toOption
        case function: ScFunction => function.functionTypeNoImplicits()
      }

      maybeType.map(substitutor.subst)
    }

  def undefineSubstitutor(typeParams: Seq[TypeParameter]): ScSubstitutor = {
    typeParams.foldLeft(ScSubstitutor.empty) {
      (subst: ScSubstitutor, tp: TypeParameter) =>
        subst.bindT(tp.nameAndId, UndefinedType(TypeParameterType(tp.psiTypeParameter)))
    }
  }

  def localTypeInference(retType: ScType, params: Seq[Parameter], exprs: Seq[Expression],
                         typeParams: Seq[TypeParameter],
                         shouldUndefineParameters: Boolean = true,
                         safeCheck: Boolean = false,
                         filterTypeParams: Boolean = true): ScTypePolymorphicType = {
    localTypeInferenceWithApplicability(retType, params, exprs, typeParams, shouldUndefineParameters, safeCheck,
      filterTypeParams)._1
  }


  class SafeCheckException extends ControlThrowable

  def localTypeInferenceWithApplicability(retType: ScType, params: Seq[Parameter], exprs: Seq[Expression],
                                          typeParams: Seq[TypeParameter],
                                          shouldUndefineParameters: Boolean = true,
                                          safeCheck: Boolean = false,
                                          filterTypeParams: Boolean = true): (ScTypePolymorphicType, Seq[ApplicabilityProblem]) = {
    val (tp, problems, _, _) = localTypeInferenceWithApplicabilityExt(retType, params, exprs, typeParams,
      shouldUndefineParameters, safeCheck, filterTypeParams)
    (tp, problems)
  }

  def localTypeInferenceWithApplicabilityExt(retType: ScType, params: Seq[Parameter], exprs: Seq[Expression],
                                             typeParams: Seq[TypeParameter],
                                             shouldUndefineParameters: Boolean = true,
                                             safeCheck: Boolean = false,
                                             filterTypeParams: Boolean = true
                                            ): (ScTypePolymorphicType, Seq[ApplicabilityProblem], Seq[(Parameter, ScExpression)], Seq[(Parameter, ScType)]) = {
    implicit val projectContext = retType.projectContext

    val nameAndIds = typeParams.map(_.nameAndId).toSet
    def hasRecursiveTypeParams(typez: ScType): Boolean = typez.hasRecursiveTypeParameters(nameAndIds)

    // See SCL-3052, SCL-3058
    // This corresponds to use of `isCompatible` in `Infer#methTypeArgs` in scalac, where `isCompatible` uses `weak_<:<`
    val s: ScSubstitutor = if (shouldUndefineParameters) undefineSubstitutor(typeParams) else ScSubstitutor.empty
    val abstractSubst = ScTypePolymorphicType(retType, typeParams).abstractTypeSubstitutor
    val paramsWithUndefTypes = params.map(p => p.copy(paramType = s.subst(p.paramType),
      expectedType = abstractSubst.subst(p.paramType), defaultType = p.defaultType.map(s.subst)))
    val c = Compatibility.checkConformanceExt(checkNames = true, paramsWithUndefTypes, exprs, checkWithImplicits = true,
      isShapesResolve = false)
    val tpe = if (c.problems.isEmpty) {
      var un: ScUndefinedSubstitutor = c.undefSubst
      val subst = c.undefSubst
      subst.getSubstitutorWithBounds(safeCheck) match {
        case Some((unSubst, lMap, uMap)) =>
          if (!filterTypeParams) {
            val undefiningSubstitutor = ScSubstitutor(typeParams.map(typeParam => {
              (typeParam.nameAndId, UndefinedType(TypeParameterType(typeParam.psiTypeParameter)))
            }).toMap)
            ScTypePolymorphicType(retType, typeParams.map {
              case tp@TypeParameter(typeParameters, lowerType, upperType, psiTypeParameter) =>
                val nameAndId = tp.nameAndId
                val lower = lMap.get(nameAndId) match {
                  case Some(_addLower) =>
                    val substedLower = unSubst.subst(lowerType)
                    val withParams = tryAddParameters(_addLower, typeParameters)

                    if (substedLower == _addLower || hasRecursiveTypeParams(substedLower)) withParams
                    else substedLower.lub(withParams)
                  case None =>
                    unSubst.subst(lowerType)
                }
                val upper = uMap.get(nameAndId) match {
                  case Some(_addUpper) =>
                    val substedUpper = unSubst.subst(upperType)
                    val withParams = tryAddParameters(_addUpper, typeParameters)

                    if (substedUpper == _addUpper || hasRecursiveTypeParams(substedUpper)) withParams
                    else substedUpper.glb(withParams)
                  case None =>
                    unSubst.subst(upperType)
                }

                if (safeCheck && !undefiningSubstitutor.subst(lower).weakConforms(undefiningSubstitutor.subst(upper)))
                  throw new SafeCheckException
                TypeParameter(typeParameters, /* doesn't important here */
                  lower,
                  upper,
                  psiTypeParameter)
            })
          } else {
            typeParams.foreach { tp =>
              val nameAndId = tp.nameAndId
              if (un.names.contains(nameAndId) || tp.lowerType != Nothing) {
                //todo: add only one of them according to variance
                if (tp.lowerType != Nothing) {
                  val substedLowerType = unSubst.subst(tp.lowerType)
                  if (!hasRecursiveTypeParams(substedLowerType)) {
                    un = un.addLower(nameAndId, substedLowerType, additional = true)
                  }
                }
                if (tp.upperType != Any) {
                  val substedUpperType = unSubst.subst(tp.upperType)
                  if (!hasRecursiveTypeParams(substedUpperType)) {
                    un = un.addUpper(nameAndId, substedUpperType, additional = true)
                  }
                }
              }
            }

            def updateWithSubst(sub: ScSubstitutor): ScTypePolymorphicType = {
              ScTypePolymorphicType(sub.subst(retType), typeParams.filter { tp =>
                val removeMe: Boolean = un.names.contains(tp.nameAndId)
                if (removeMe && safeCheck) {
                  //let's check type parameter kinds
                  def checkTypeParam(typeParam: ScTypeParam, tp: => ScType): Boolean = {
                    val typeParams: Seq[ScTypeParam] = typeParam.typeParameters
                    if (typeParams.isEmpty) return true
                    tp match {
                      case ParameterizedType(_, typeArgs) =>
                        if (typeArgs.length != typeParams.length) return false
                        typeArgs.zip(typeParams).forall {
                          case (tp: ScType, typeParam: ScTypeParam) => checkTypeParam(typeParam, tp)
                        }
                      case _ =>
                        def checkNamed(named: PsiNamedElement, typeParams: Seq[ScTypeParam]): Boolean = {
                          named match {
                            case t: ScTypeParametersOwner =>
                              if (typeParams.length != t.typeParameters.length) return false
                              typeParams.zip(t.typeParameters).forall {
                                case (p1: ScTypeParam, p2: ScTypeParam) =>
                                  if (p1.typeParameters.nonEmpty) checkNamed(p2, p1.typeParameters)
                                  else true
                              }
                            case p: PsiTypeParameterListOwner =>
                              if (typeParams.length != p.getTypeParameters.length) return false
                              typeParams.forall(_.typeParameters.isEmpty)
                            case _ => false
                          }
                        }
                        tp.extractDesignated(expandAliases = false).exists(checkNamed(_, typeParams))
                    }
                  }
                  tp.psiTypeParameter match {
                    case typeParam: ScTypeParam =>
                      if (!checkTypeParam(typeParam, sub.subst(TypeParameterType(tp.psiTypeParameter))))
                        throw new SafeCheckException
                    case _ =>
                  }
                }
                !removeMe
              }.map {
                case TypeParameter(typeParameters, lowerType, upperType, psiTypeParameter) =>
                  TypeParameter(typeParameters, /* doesn't important here */
                    sub.subst(lowerType),
                    sub.subst(upperType),
                    psiTypeParameter)
              })
            }

            un.getSubstitutor match {
              case Some(unSubstitutor) => updateWithSubst(unSubstitutor)
              case _ if safeCheck => throw new SafeCheckException
              case _ => updateWithSubst(unSubst)
            }
          }
        case None => throw new SafeCheckException
      }
    } else ScTypePolymorphicType(retType, typeParams)
    (tpe, c.problems, c.matchedArgs, c.matchedTypes)
  }

  private def tryAddParameters(desType: ScType, typeParameters: Seq[TypeParameter]): ScType = {
    if (typeParameters.nonEmpty && !desType.isInstanceOf[ScParameterizedType] &&
      !typeParameters.exists(_.name == "_"))
      ScParameterizedType(desType, typeParameters.map(TypeParameterType(_)))
    else desType
  }
}
