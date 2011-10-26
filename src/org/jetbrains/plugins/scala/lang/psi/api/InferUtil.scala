package org.jetbrains.plugins.scala.lang.psi.api

import base.patterns.ScBindingPattern
import expr.ScExpression
import collection.mutable.ArrayBuffer
import org.jetbrains.plugins.scala.lang.psi.types.Compatibility.Expression
import org.jetbrains.plugins.scala.lang.resolve.ScalaResolveResult
import org.jetbrains.plugins.scala.lang.psi.implicits.ImplicitParametersCollector
import statements.params.ScParameter
import statements.ScFunction
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiUtil.SafeCheckException
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiUtil
import com.intellij.psi.PsiElement
import org.jetbrains.plugins.scala.lang.psi.types.result.{Success, TypeResult, TypingContext}
import org.jetbrains.plugins.scala.lang.psi.types._
import nonvalue.{Parameter, ScMethodType, ScTypePolymorphicType}
import toplevel.typedef.ScObject

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
                                       check: Boolean, withEtaExpansion: Boolean): (ScType, Option[Seq[ScalaResolveResult]]) = {
    var resInner = res
    var implicitParameters: Option[Seq[ScalaResolveResult]] = None
    res match {
      case t@ScTypePolymorphicType(mt@ScMethodType(retType, params, impl), typeParams) if !impl && withEtaExpansion =>
        // See SCL-3516
        val (updatedType, ps) = 
          updateTypeWithImplicitParameters(t.copy(internalType = retType), element, check, withEtaExpansion)
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
        val resolveResults = new ArrayBuffer[ScalaResolveResult]
        val iterator = params.iterator
        while (iterator.hasNext) {
          val param = iterator.next()
          val paramType = abstractSubstitutor.subst(param.paramType) //we should do all of this with information known before
          val concreteParamType = polymorphicSubst.subst(param.paramType)
          val collector = new ImplicitParametersCollector(element, paramType, concreteParamType)
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
                      case other => other
                    }
                  }
                  else subst.subst(fun.getType(TypingContext.empty).get)
                }
                exprs += new Expression(polymorphicSubst subst funType)
              }
            }
          } else {
            if (check) {
              //check if it's ClassManifest parameter:
              ScType.extractClass(paramType, Some(element.getProject)) match {
                case Some(clazz) if clazz.getQualifiedName == "scala.reflect.ClassManifest" => //do not throw, it's safe
                case _ =>
                  throw new SafeCheckException
              }
            }
            resolveResults += null
            exprs += new Expression(Any)
          }
        }
        implicitParameters = Some(resolveResults.toSeq)
        resInner = ScalaPsiUtil.localTypeInference(retType, params, exprs.toSeq, typeParams, safeCheck = check)
      }
      case mt@ScMethodType(retType, params, isImplicit) if !isImplicit && withEtaExpansion =>
        // See SCL-3516
        val (updatedType, ps) = updateTypeWithImplicitParameters(retType, element, check, withEtaExpansion)
        implicitParameters = ps
        resInner = mt.copy(returnType = updatedType)(mt.project, mt.scope)
      case ScMethodType(retType, params, isImplicit) if isImplicit => {
        val resolveResults = new ArrayBuffer[ScalaResolveResult]
        val iterator = params.iterator
        while (iterator.hasNext) {
          val param = iterator.next()
          val paramType = param.paramType //we should do all of this with information known before
          val collector = new ImplicitParametersCollector(element, paramType, paramType /*TODO?*/)
          val results = collector.collect
          if (results.length == 1) {
            resolveResults += results(0)
          } else {
            resolveResults += null
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
   * @param fromUnderscoreSection we shouldn't update if it's anonymous function
   *                              also we can update just for simple type without function
   * @param expectedType appropriate expected type
   * @param expr place
   * @param check we fail to get right type then if check throw SafeCheckException
   * @return updated type
   */
  def updateAccordingToExpectedType(_nonValueType: TypeResult[ScType], fromUnderscoreSection: Boolean,
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
            Seq(Parameter("", expected, expected, false, false, false)),
            Seq(new Expression(ScalaPsiUtil.undefineSubstitutor(typeParams).subst(innerInternal.inferValueType))),
            typeParams, shouldUndefineParameters = false, safeCheck = check)
          nonValueType = Success(update, Some(expr)) //here should work in different way:
        }
        if (!fromUnderscoreSection) {
          updateRes(expectedType.get)
        } else {
          expectedType.get match {
            case ScFunctionType(retType, _) => updateRes(retType)
            case p: ScParameterizedType => p.getFunctionType match {
              case Some(ScFunctionType(retType, _)) => updateRes(retType)
              case _ =>
            }
            case _ => //do not update res, we haven't expected type
          }
        }
      }
      //todo: Something should be unified, that's bad to have fromImplicitParameters parameter.
      case Success(ScTypePolymorphicType(internal, typeParams), _) if expectedType != None && fromImplicitParameters => {
        def updateRes(expected: ScType) {
          nonValueType = Success(ScalaPsiUtil.localTypeInference(internal,
            Seq(Parameter("", expected, expected, false, false, false)),
              Seq(new Expression(ScalaPsiUtil.undefineSubstitutor(typeParams).subst(internal.inferValueType))),
            typeParams, shouldUndefineParameters = false, safeCheck = check), Some(expr)) //here should work in different way:
        }
        if (!fromUnderscoreSection) {
          updateRes(expectedType.get)
        } else {
          expectedType.get match {
            case ScFunctionType(retType, _) => updateRes(retType)
            case p: ScParameterizedType => p.getFunctionType match {
              case Some(ScFunctionType(retType, _)) => updateRes(retType)
              case _ =>
            }
            case _ => //do not update res, we haven't expected type
          }
        }
      }
      case _ =>
    }
    nonValueType
  }
}