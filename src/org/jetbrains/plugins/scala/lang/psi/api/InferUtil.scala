package org.jetbrains.plugins.scala.lang.psi.api

import base.patterns.ScBindingPattern
import base.ScLiteral
import expr.ScExpression
import collection.mutable.ArrayBuffer
import org.jetbrains.plugins.scala.lang.psi.types.Compatibility.Expression
import org.jetbrains.plugins.scala.lang.resolve.ScalaResolveResult
import org.jetbrains.plugins.scala.lang.psi.implicits.ImplicitParametersCollector
import statements.params.ScParameter
import statements.ScFunction
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiUtil.SafeCheckException
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiUtil
import org.jetbrains.plugins.scala.lang.psi.types.result.{Success, TypeResult, TypingContext}
import org.jetbrains.plugins.scala.lang.psi.types._
import nonvalue.{Parameter, ScMethodType, ScTypePolymorphicType}
import toplevel.typedef.ScObject
import com.intellij.psi.PsiElement
import org.jetbrains.plugins.scala.extensions.toPsiClassExt
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementFactory

/**
 * @author Alexander Podkhalyuzin
 */

object InferUtil {
  /**
   * This method can find implicit parameters for given MethodType
   * @param res MethodType or PolymorphicType(MethodType)
   * @param element place to find implicit parameters
   * @param check if true can throw SafeCheckException if it not found not ambiguous implicit parameters
   * @return updated type and sequence of implicit parameters
   */
  def updateTypeWithImplicitParameters(res: ScType, element: PsiElement,
                                       check: Boolean,
                                       searchImplicitsRecursively: Boolean = true): (ScType, Option[Seq[ScalaResolveResult]]) = {
    var resInner = res
    var implicitParameters: Option[Seq[ScalaResolveResult]] = None
    res match {
      case t@ScTypePolymorphicType(mt@ScMethodType(retType, params, impl), typeParams) if !impl =>
        // See SCL-3516
        val (updatedType, ps) = 
          updateTypeWithImplicitParameters(t.copy(internalType = retType), element, check)
        implicitParameters = ps
        updatedType match {
          case tpt: ScTypePolymorphicType =>
            resInner = t.copy(internalType = mt.copy(returnType = tpt.internalType)(mt.project, mt.scope),
              typeParameters = tpt.typeParameters)
          case _ => //shouldn't be there
            resInner = t.copy(internalType = mt.copy(returnType = updatedType)(mt.project, mt.scope))
        }
      case t@ScTypePolymorphicType(ScMethodType(retType, params, impl), typeParams) if impl => {
        val polymorphicSubst = t.polymorphicTypeSubstitutor
        val abstractSubstitutor: ScSubstitutor = t.abstractTypeSubstitutor
        val exprs = new ArrayBuffer[Expression]
        val paramsForInfer = new ArrayBuffer[Parameter]()
        val resolveResults = new ArrayBuffer[ScalaResolveResult]
        val iterator = params.iterator
        while (iterator.hasNext) {
          val param = iterator.next()
          val paramType = abstractSubstitutor.subst(param.paramType) //we should do all of this with information known before
          val collector = new ImplicitParametersCollector(element, paramType, searchImplicitsRecursively)
          val results = collector.collect
          if (results.length == 1) {
            resolveResults += results(0)
            results(0) match {
              case ScalaResolveResult(o: ScObject, subst) =>
                exprs += new Expression(polymorphicSubst subst subst.subst(o.getType(TypingContext.empty).get))
              case ScalaResolveResult(param: ScParameter, subst) =>
                exprs += new Expression(polymorphicSubst subst subst.subst(param.getType(TypingContext.empty).get))
              case ScalaResolveResult(patt: ScBindingPattern, subst) => {
                exprs += new Expression(polymorphicSubst subst subst.subst(patt.getType(TypingContext.empty).get))
              }
              case ScalaResolveResult(fun: ScFunction, subst) => {
                val funType = {
                  if (fun.parameters.length == 0 || fun.paramClauses.clauses.apply(0).isImplicit) {
                    subst.subst(fun.getType(TypingContext.empty).get) match {
                      case ScFunctionType(ret, _) => ret
                      case p: ScParameterizedType =>
                        p.getFunctionType match {
                          case Some(ScFunctionType(ret, _)) => ret
                          case _ => p
                        }
                      case other => other
                    }
                  }
                  else subst.subst(fun.getType(TypingContext.empty).get)
                }
                exprs += new Expression(polymorphicSubst subst funType)
              }
            }
            paramsForInfer += param
          } else {
            def checkManifest(fun: ScalaResolveResult => Unit) {
              val result = paramType match {
                case p@ScParameterizedType(des, Seq(arg)) =>
                  ScType.extractClass(des) match {
                    case Some(clazz) if clazz.qualifiedName == "scala.reflect.ClassManifest"  ||
                      clazz.qualifiedName == "scala.reflect.Manifest" ||
                      clazz.qualifiedName == "scala.reflect.ClassTag" =>
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
              if (r == null && check) throw new SafeCheckException
              else resolveResults += r
            })
          }
        }
        implicitParameters = Some(resolveResults.toSeq)
        resInner = ScalaPsiUtil.localTypeInference(retType, paramsForInfer, exprs, typeParams, safeCheck = check)
      }
      case mt@ScMethodType(retType, params, isImplicit) if !isImplicit =>
        // See SCL-3516
        val (updatedType, ps) = updateTypeWithImplicitParameters(retType, element, check)
        implicitParameters = ps
        resInner = mt.copy(returnType = updatedType)(mt.project, mt.scope)
      case ScMethodType(retType, params, isImplicit) if isImplicit => {
        val resolveResults = new ArrayBuffer[ScalaResolveResult]
        val iterator = params.iterator
        while (iterator.hasNext) {
          val param = iterator.next()
          val paramType = param.paramType //we should do all of this with information known before
          val collector = new ImplicitParametersCollector(element, paramType, searchImplicitsRecursively)
          val results = collector.collect
          if (results.length == 1) {
            resolveResults += results(0)
          } else {
            //check if it's ClassManifest parameter:
            paramType match {
              case p@ScParameterizedType(des, Seq(arg)) =>
                ScType.extractClass(des) match {
                  case Some(clazz) if clazz.qualifiedName == "scala.reflect.ClassManifest"  ||
                    clazz.qualifiedName == "scala.reflect.Manifest" ||
                    clazz.qualifiedName == "scala.reflect.ClassTag" =>
                    //do not throw, it's safe
                    resolveResults += new ScalaResolveResult(clazz, p.substitutor)
                  case _ =>
                    if (check) throw new SafeCheckException
                    resolveResults += null
                }
              case _ =>
                if (check) throw new SafeCheckException
                resolveResults += null
            }
          }
        }
        implicitParameters = Some(resolveResults.toSeq)
        resInner = retType
      }
      case _ =>
    }
    (resInner, implicitParameters)
  }

  /**
   * Util method to update type accoding to expected type
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
                                    expectedType: Option[ScType], expr: ScExpression,
                                    check: Boolean): TypeResult[ScType] = {
    var nonValueType = _nonValueType
    nonValueType match {
      case Success(ScTypePolymorphicType(m@ScMethodType(internal, params, impl), typeParams), _)
        if expectedType != None && (!fromImplicitParameters || impl) => {
        def updateRes(expected: ScType) {
          if (expected.equiv(Unit)) return //do not update according to Unit type
          val innerInternal = internal match {
            case ScMethodType(innerInternal, _, innerImpl) if innerImpl && !fromImplicitParameters => innerInternal
            case _ => internal
          }
          val update: ScTypePolymorphicType = ScalaPsiUtil.localTypeInference(m,
            Seq(Parameter("", expected, expected, isDefault = false, isRepeated = false, isByName = false)),
            Seq(new Expression(ScalaPsiUtil.undefineSubstitutor(typeParams).subst(innerInternal.inferValueType))),
            typeParams, shouldUndefineParameters = false, safeCheck = check, filterTypeParams = false)
          nonValueType = Success(update, Some(expr)) //here should work in different way:
        }
        updateRes(expectedType.get)
      }
      //todo: Something should be unified, that's bad to have fromImplicitParameters parameter.
      case Success(ScTypePolymorphicType(internal, typeParams), _) if expectedType != None && fromImplicitParameters => {
        def updateRes(expected: ScType) {
          nonValueType = Success(ScalaPsiUtil.localTypeInference(internal,
            Seq(Parameter("", expected, expected, isDefault = false, isRepeated = false, isByName = false)),
              Seq(new Expression(ScalaPsiUtil.undefineSubstitutor(typeParams).subst(internal.inferValueType))),
            typeParams, shouldUndefineParameters = false, safeCheck = check,
            filterTypeParams = false), Some(expr)) //here should work in different way:
        }
        updateRes(expectedType.get)
      }
      case _ =>
    }

    // interim fix for SCL-3905.
    def applyImplicitViewToResult(mt: ScMethodType, expectedType: Option[ScType]): ScType = {
      expectedType.flatMap(ScType.extractFunctionType) match {
        case Some(expectedType@ScFunctionType(expectedRet, expectedParams)) if expectedParams.length == mt.params.length
          && !mt.returnType.conforms(expectedType) =>
          mt.returnType match {
            case methodType: ScMethodType => return mt.copy(
              returnType = applyImplicitViewToResult(methodType, Some(expectedRet)))(mt.project, mt.scope)
            case _ =>
          }
          val dummyExpr = ScalaPsiElementFactory.createExpressionWithContextFromText("null", expr.getContext, expr)
          dummyExpr.asInstanceOf[ScLiteral].setTypeWithoutImplicits(Some(mt.returnType))
          val updatedResultType = dummyExpr.getTypeAfterImplicitConversion(expectedOption = Some(expectedRet))

          expr.setAdditionalExpression(Some(dummyExpr, expectedRet))

          new ScMethodType(updatedResultType.tr.getOrElse(mt.returnType), mt.params, mt.isImplicit)(mt.project, mt.scope)
        case x => mt
      }
    }

    nonValueType.map {
      case tpt @ ScTypePolymorphicType(mt: ScMethodType, typeParams) => tpt.copy(internalType = applyImplicitViewToResult(mt, expectedType))
      case mt: ScMethodType => applyImplicitViewToResult(mt, expectedType)
      case tp => tp
    }
  }
}