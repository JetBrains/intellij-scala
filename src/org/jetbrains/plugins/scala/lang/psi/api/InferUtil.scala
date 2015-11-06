package org.jetbrains.plugins.scala
package lang.psi.api

import com.intellij.openapi.diagnostic.Logger
import com.intellij.psi.PsiElement
import org.jetbrains.plugins.scala.caches.ScalaRecursionManager
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiUtil.SafeCheckException
import org.jetbrains.plugins.scala.lang.psi.api.base.patterns.ScBindingPattern
import org.jetbrains.plugins.scala.lang.psi.api.base.{ScFieldId, ScLiteral}
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScExpression
import org.jetbrains.plugins.scala.lang.psi.api.macros.{MacroContext, ScalaMacroEvaluator}
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScFunction
import org.jetbrains.plugins.scala.lang.psi.api.statements.params.ScParameter
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.ScNamedElement
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScObject
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementFactory
import org.jetbrains.plugins.scala.lang.psi.implicits.ImplicitCollector
import org.jetbrains.plugins.scala.lang.psi.implicits.ImplicitCollector.ImplicitState
import org.jetbrains.plugins.scala.lang.psi.types.Compatibility.Expression
import org.jetbrains.plugins.scala.lang.psi.types._
import org.jetbrains.plugins.scala.lang.psi.types.nonvalue.{Parameter, ScMethodType, ScTypePolymorphicType}
import org.jetbrains.plugins.scala.lang.psi.types.result.{Success, TypeResult, TypingContext}
import org.jetbrains.plugins.scala.lang.psi.{ScalaPsiUtil, types}
import org.jetbrains.plugins.scala.lang.resolve.ScalaResolveResult
import org.jetbrains.plugins.scala.project.ScalaLanguageLevel.Scala_2_10
import org.jetbrains.plugins.scala.project._

import scala.collection.mutable.ArrayBuffer

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
   * @param res MethodType or PolymorphicType(MethodType)
   * @param element place to find implicit parameters
   * @param check if true can throw SafeCheckException if it not found not ambiguous implicit parameters
   * @return updated type and sequence of implicit parameters
   */
  def updateTypeWithImplicitParameters(res: ScType, element: PsiElement, coreElement: Option[ScNamedElement], check: Boolean,
                                       searchImplicitsRecursively: Int = 0, fullInfo: Boolean): (ScType, Option[Seq[ScalaResolveResult]]) = {
    var resInner = res
    var implicitParameters: Option[Seq[ScalaResolveResult]] = None
    res match {
      case t@ScTypePolymorphicType(mt@ScMethodType(retType, params, impl), typeParams) if !impl =>
        // See SCL-3516
        val (updatedType, ps) = 
          updateTypeWithImplicitParameters(t.copy(internalType = retType), element, coreElement, check, fullInfo = fullInfo)
        implicitParameters = ps
        updatedType match {
          case tpt: ScTypePolymorphicType =>
            resInner = t.copy(internalType = mt.copy(returnType = tpt.internalType)(mt.project, mt.scope),
              typeParameters = tpt.typeParameters)
          case _ => //shouldn't be there
            resInner = t.copy(internalType = mt.copy(returnType = updatedType)(mt.project, mt.scope))
        }
      case t@ScTypePolymorphicType(mt@ScMethodType(retType, params, impl), typeParams) if impl =>
        val fullAbstractSubstitutor = t.abstractTypeSubstitutor
        val coreTypes = params.map(p => fullAbstractSubstitutor.subst(p.paramType))
        val splitMethodType = params.reverse.foldLeft(retType) {
          case (tp: ScType, param: Parameter) => ScMethodType(tp, Seq(param), isImplicit = true)(mt.project, mt.scope)
        }
        resInner = ScTypePolymorphicType(splitMethodType, typeParams)
        val paramsForInferBuffer = new ArrayBuffer[Parameter]()
        val exprsBuffer = new ArrayBuffer[Compatibility.Expression]()
        val resolveResultsBuffer = new ArrayBuffer[ScalaResolveResult]()
        coreTypes.foreach {
          case coreType =>
            resInner match {
              case t@ScTypePolymorphicType(mt@ScMethodType(retTypeSingle, paramsSingle, _), typeParamsSingle) =>
                val polymorphicSubst = t.polymorphicTypeSubstitutor
                val abstractSubstitutor: ScSubstitutor = t.abstractTypeSubstitutor
                val (paramsForInfer, exprs, resolveResults) =
                  findImplicits(paramsSingle, coreElement, element, check, searchImplicitsRecursively, abstractSubstitutor, polymorphicSubst)
                resInner = ScalaPsiUtil.localTypeInference(retTypeSingle, paramsForInfer, exprs, typeParamsSingle,
                  safeCheck = check || fullInfo)
                paramsForInferBuffer ++= paramsForInfer
                exprsBuffer ++= exprs
                resolveResultsBuffer ++= resolveResults
            }
        }
        implicitParameters = Some(resolveResultsBuffer.toSeq)
        val dependentSubst = new ScSubstitutor(() => {
          val level = element.languageLevel
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
      case mt@ScMethodType(retType, params, isImplicit) if !isImplicit =>
        // See SCL-3516
        val (updatedType, ps) = updateTypeWithImplicitParameters(retType, element, coreElement, check, fullInfo = fullInfo)
        implicitParameters = ps
        resInner = mt.copy(returnType = updatedType)(mt.project, mt.scope)
      case ScMethodType(retType, params, isImplicit) if isImplicit =>
        val (paramsForInfer, exprs, resolveResults) =
          findImplicits(params, coreElement, element, check, searchImplicitsRecursively)

        implicitParameters = Some(resolveResults.toSeq)
        resInner = retType
        val dependentSubst = new ScSubstitutor(() => {
          val level = element.languageLevel
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
                    polymorphicSubst: ScSubstitutor = ScSubstitutor.empty): (Seq[Parameter], Seq[Compatibility.Expression], Seq[ScalaResolveResult]) = {
    val exprs = new ArrayBuffer[Expression]
    val paramsForInfer = new ArrayBuffer[Parameter]()
    val resolveResults = new ArrayBuffer[ScalaResolveResult]
    val iterator = params.iterator
    while (iterator.hasNext) {
      val param = iterator.next()
      val paramType = abstractSubstitutor.subst(param.paramType) //we should do all of this with information known before
      val implicitState = ImplicitState(place, paramType, paramType, coreElement, isImplicitConversion = false,
          isExtensionConversion = false, searchImplicitsRecursively, None, Some(ScalaRecursionManager.recursionMap.get()))
      val collector = new ImplicitCollector(implicitState)
      val results = collector.collect()
      if (results.length == 1) {
        if (check && !results.head.isApplicable()) throw new SafeCheckException
        resolveResults += results.head
        def updateExpr() {
          exprs += new Expression(polymorphicSubst subst extractImplicitParameterType(results.head))
        }
        val evaluator = ScalaMacroEvaluator.getInstance(place.getProject)
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
            case p@ScParameterizedType(des, Seq(arg)) =>
              ScType.extractClass(des) match {
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
            val parameter = ScalaPsiElementFactory.createParameterFromText(s"$notFoundParameterName: Int", place.getManager)
            resolveResults += new ScalaResolveResult(parameter, implicitSearchState = Some(implicitState))
          } else resolveResults += r
        })
      }
    }
    (paramsForInfer.toSeq, exprs.toSeq, resolveResults.toSeq)
  }

  /**
   * Util method to update type according to expected type
   * @param _nonValueType type, to update it should be PolymorphicType(MethodType)
   * @param fromImplicitParameters we shouldn't update if it's anonymous function
   *                               also we can update just for simple type without function
   * @param expectedType appropriate expected type
   * @param expr place
   * @param check we fail to get right type then if check throw SafeCheckException
   * @return updated type
   */
  def updateAccordingToExpectedType(_nonValueType: TypeResult[ScType],
                                    fromImplicitParameters: Boolean,
                                    filterTypeParams: Boolean,
                                    expectedType: Option[ScType], expr: PsiElement,
                                    check: Boolean, forEtaExpansion: Boolean = false): TypeResult[ScType] = {
    var nonValueType = _nonValueType
    nonValueType match {
      case Success(ScTypePolymorphicType(m@ScMethodType(internal, params, impl), typeParams), _)
        if expectedType.isDefined && (!fromImplicitParameters || impl) =>
        def updateRes(expected: ScType) {
          if (expected.equiv(types.Unit)) return //do not update according to Unit type
          val innerInternal = internal match {
            case ScMethodType(inter, _, innerImpl) if innerImpl && !fromImplicitParameters => inter
            case _ => internal
          }
          val update: ScTypePolymorphicType = ScalaPsiUtil.localTypeInference(m,
            Seq(Parameter("", None, expected, expected, isDefault = false, isRepeated = false, isByName = false)),
            Seq(new Expression(ScalaPsiUtil.undefineSubstitutor(typeParams).subst(innerInternal.inferValueType))),
            typeParams, shouldUndefineParameters = false, safeCheck = check, filterTypeParams = filterTypeParams)
          nonValueType = Success(update, Some(expr)) //here should work in different way:
        }
        updateRes(expectedType.get)
      //todo: Something should be unified, that's bad to have fromImplicitParameters parameter.
      case Success(ScTypePolymorphicType(internal, typeParams), _) if expectedType.isDefined && fromImplicitParameters =>
        def updateRes(expected: ScType) {
          nonValueType = Success(ScalaPsiUtil.localTypeInference(internal,
            Seq(Parameter("", None, expected, expected, isDefault = false, isRepeated = false, isByName = false)),
              Seq(new Expression(ScalaPsiUtil.undefineSubstitutor(typeParams).subst(internal.inferValueType))),
            typeParams, shouldUndefineParameters = false, safeCheck = check,
            filterTypeParams = filterTypeParams), Some(expr)) //here should work in different way:
        }
        updateRes(expectedType.get)
      case _ =>
    }

    if (!expr.isInstanceOf[ScExpression]) return nonValueType

    // interim fix for SCL-3905.
    def applyImplicitViewToResult(mt: ScMethodType, expectedType: Option[ScType], fromSAM: Boolean = false): ScType = {
      expectedType match {
        case Some(expectedType@ScFunctionType(expectedRet, expectedParams)) if expectedParams.length == mt.params.length
          && !mt.returnType.conforms(expectedType) =>
          mt.returnType match {
            case methodType: ScMethodType => return mt.copy(
              returnType = applyImplicitViewToResult(methodType, Some(expectedRet), fromSAM))(mt.project, mt.scope)
            case _ =>
          }
          val dummyExpr = ScalaPsiElementFactory.createExpressionWithContextFromText("null", expr.getContext, expr)
          dummyExpr.asInstanceOf[ScLiteral].setTypeWithoutImplicits(Some(mt.returnType))
          val updatedResultType = dummyExpr.getTypeAfterImplicitConversion(expectedOption = Some(expectedRet))

          expr.asInstanceOf[ScExpression].setAdditionalExpression(Some(dummyExpr, expectedRet))

          new ScMethodType(updatedResultType.tr.getOrElse(mt.returnType), mt.params, mt.isImplicit)(mt.project, mt.scope)
        case Some(tp) if !fromSAM && ScalaPsiUtil.isSAMEnabled(expr) && forEtaExpansion =>
          ScalaPsiUtil.toSAMType(tp, expr.getResolveScope) match {
            case Some(ScFunctionType(retTp, _)) if retTp.equiv(Unit) =>
              //example:
              //def f(): () => Unit = () => println()
              //val f1: Runnable = f
              ScFunctionType(Unit, Seq.empty)(expr.getProject, expr.getResolveScope)
            case _ => applyImplicitViewToResult(mt, ScalaPsiUtil.toSAMType(tp, expr.getResolveScope), fromSAM = true)
          }
        case _ => mt
      }
    }

    nonValueType.map {
      case tpt @ ScTypePolymorphicType(mt: ScMethodType, typeParams) => tpt.copy(internalType = applyImplicitViewToResult(mt, expectedType))
      case mt: ScMethodType => applyImplicitViewToResult(mt, expectedType)
      case tp => tp
    }
  }

  def extractImplicitParameterType(r: ScalaResolveResult): ScType = {
    r match {
      case r: ScalaResolveResult if r.implicitParameterType.isDefined => r.implicitParameterType.get
      case ScalaResolveResult(o: ScObject, subst) => subst.subst(o.getType(TypingContext.empty).get)
      case ScalaResolveResult(param: ScParameter, subst) => subst.subst(param.getType(TypingContext.empty).get)
      case ScalaResolveResult(patt: ScBindingPattern, subst) => subst.subst(patt.getType(TypingContext.empty).get)
      case ScalaResolveResult(f: ScFieldId, subst) => subst.subst(f.getType(TypingContext.empty).get)
      case ScalaResolveResult(fun: ScFunction, subst) => subst.subst(fun.getTypeNoImplicits(TypingContext.empty).get)
    }
  }
}
