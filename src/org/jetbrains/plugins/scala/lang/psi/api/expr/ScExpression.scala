package org.jetbrains.plugins.scala
package lang
package psi
package api
package expr

import impl.ScalaPsiElementFactory
import types.nonvalue.{TypeParameter, ScMethodType, Parameter, ScTypePolymorphicType}
import types.result.{Success, Failure, TypingContext, TypeResult}
import toplevel.imports.usages.ImportUsed
import types.Compatibility.Expression
import base.patterns.ScBindingPattern
import resolve.ScalaResolveResult
import implicits.{ImplicitParametersCollector, ScImplicitlyConvertible}
import collection.mutable.ArrayBuffer
import statements.params.ScParameter
import psi.{ScalaPsiUtil}
import types._
import nonvalue._
import collection.{Set, Seq}
import statements.{ScFunctionDefinition, ScFunction}
import resolve.processor.MostSpecificUtil
import com.intellij.openapi.progress.ProgressManager
import com.intellij.psi.impl.source.resolve.ResolveCache
import caches.CachesUtil
import com.intellij.psi.{PsiNamedElement, PsiElement, PsiInvalidElementAccessException}

/**
 * @author ilyas, Alexander Podkhalyuzin
 */

trait ScExpression extends ScBlockStatement with ScImplicitlyConvertible {
  import ScExpression._
  /**
   * This method returns real type, after using implicit conversions.
   * Second parameter to return is used imports for this conversion.
   * @param expectedOption to which type we tring to convert
   */
  def getTypeAfterImplicitConversion(exp: Option[Option[ScType]] = None, 
                                     checkImplicits: Boolean = true): ExpressionTypeResult = {
    val expectedOption = exp match {
      case Some(a) => a
      case _ => expectedType
    }
    def inner: ExpressionTypeResult = {
      val expected: ScType = expectedOption match {
        case Some(a) => a
        case _ => return ExpressionTypeResult(getType(TypingContext.empty), Set.empty, None)
      }
      //now we want to change context for this expression, for example
      //we want to imagine that expected type for this expression is...
      def anon(expr: ScExpression): Boolean = {
        getText.indexOf("_") match {
          case -1 => false //optimization
          case _ => {
            val unders = ScUnderScoreSectionUtil.underscores(this)
            if (unders.length != 0) {
              return true
            }
          }
        }
        expr match {
          case b: ScBlockExpr if b.isAnonymousFunction => true
          case b: ScBlockExpr => b.lastExpr match {case Some(x) => anon(x) case _ => false}
          case p: ScParenthesisedExpr => p.expr match {case Some(x) => anon(x) case _ => false}
          case i: ScIfStmt if i.elseBranch != None =>
            anon(i.elseBranch.get) || (i.thenBranch match {case Some(x) => anon(x) case _ => false})
          case td: ScTryBlock => td.lastExpr match {case Some(x) => anon(x) case _ => false}
          case m: ScMatchStmt => m.getBranches.foldLeft(false)((x, y) => x || anon(y))
          case f: ScFunctionExpr =>  f.parameters.exists(p => p.typeElement == None)
          case _ => false
        }
      }

      def forExpr(expr: ScExpression): ExpressionTypeResult = {
        val tr = expr.getType(TypingContext.empty)
        val defaultResult: ExpressionTypeResult = ExpressionTypeResult(tr, Set.empty, None)
        val tp = tr.getOrElse(return defaultResult)
        //if this result is ok, we do not need to think about implicits
        if (tp.conforms(expected)) return defaultResult
        if (!checkImplicits) return defaultResult

        //this functionality for checking if this expression can be implicitly changed and then
        //it will conform to expected type
        val implicitMap: Seq[(ScType, PsiNamedElement, Set[ImportUsed])] = expr.implicitMap
        val f: Seq[(ScType, PsiNamedElement, Set[ImportUsed])] = implicitMap.filter(_._1.conforms(expected))
        if (f.length == 1) return ExpressionTypeResult(Success(f(0)._1, Some(this)), f(0)._3, Some(f(0)._2))
        else if (f.length == 0) return defaultResult
        else {
          var res = MostSpecificUtil(this, 1).mostSpecificForImplicit(f.toSet).getOrElse(return defaultResult)
          return ExpressionTypeResult(Success(res._1, Some(this)), res._3, Some(res._2))
        }
      }

      if (exp != None) {
        //this substitutor is importatant in case for anon functions in currings, to apply to params known information
        val subst: ScSubstitutor = getContext match {
          case args: ScArgumentExprList => {
            args.callExpression match {
              case call: ScMethodCall => {
                call.getNonValueType(TypingContext.empty) match {
                  case Success(tp: ScTypePolymorphicType, _) => tp.polymorphicTypeSubstitutor
                  case _ => ScSubstitutor.empty
                }
              }
              case _ => ScSubstitutor.empty
            }
          }
          case _ => ScSubstitutor.empty
        }
        val tps = expectedOption.toList.toArray.map(_ match {
          case p@ScParameterizedType(des, typeArgs) => p.getFunctionType match {
            case Some(ScFunctionType(retType, params)) => ScParameterizedType(des, params.map(subst subst _) ++ Seq(retType))
            case _ => p
          }
          case ScFunctionType(retType, params) => new ScFunctionType(retType, params.map(subst subst _),
            getProject, getResolveScope)
          case ScMethodType(retType, params, isImpl) => new ScMethodType(retType,
            params.map(p => Parameter(p.name, subst.subst(p.paramType), p.isDefault,p.isRepeated)), isImpl,
            getProject, getResolveScope)
          case ScTypePolymorphicType(ScMethodType(retType, params, isImpl), typeParams) => {
            ScTypePolymorphicType(new ScMethodType(retType,
              params.map(p => Parameter(p.name, subst.subst(p.paramType), p.isDefault,p.isRepeated)), isImpl,
              getProject, getResolveScope),
              typeParams)
          }
          case tp => tp
        })

        //this is important for thread safe calculations. Because we are using mutable state for expression
        //todo: possible fix to remove mutable state, and to use TypingContext for type calculation.
        if (!anon(this)) {
          var b: java.lang.Boolean = null
          EXPR_LOCK synchronized {
            b = getUserData(CachesUtil.EXPRESSION_TYPING_KEY)
            if (b == null) {
              putUserData(CachesUtil.EXPRESSION_TYPING_KEY, java.lang.Boolean.TRUE)
            }
          }
          if (b == null) {
            //save data
            val data = (expectedTypesCache, expectedTypesModCount, exprType, exprTypeModCount)
            expectedTypesCache = tps
            expectedTypesModCount = getManager.getModificationTracker.getModificationCount
            exprType = null
            try {
              forExpr(this)
            }
            finally {
              //load data
              expectedTypesCache = data._1;expectedTypesModCount = data._2;exprType = data._3;exprTypeModCount = data._4
              putUserData(CachesUtil.EXPRESSION_TYPING_KEY, null)
            }
          } else {
            val newExpr = ScalaPsiElementFactory.createExpressionWithContextFromText(getText, getContext, this)
            newExpr.setExpectedTypes(tps)
            forExpr(newExpr)
          }
        } else {
          val newExpr = ScalaPsiElementFactory.createExpressionWithContextFromText(getText, getContext, this)
          newExpr.setExpectedTypes(tps)
          forExpr(newExpr)
        }
      } else {
        forExpr(this)
      }
    }
    if (exp != None || !checkImplicits) return inner //no cache with strange parameters

    //caching
    var tp = exprAfterImplicitType
    var curModCount = getManager.getModificationTracker.getModificationCount
    if (tp != null && curModCount == exprTypeAfterImplicitModCount) {
      return tp
    }
    tp = inner
    exprAfterImplicitType = tp
    exprTypeAfterImplicitModCount = curModCount
    return tp
  }

  private val EXPR_LOCK = new Object()

  def getType(ctx: TypingContext): TypeResult[ScType] = {
    ProgressManager.checkCanceled
    if (ctx != TypingContext.empty) return valueType(ctx)
    var tp = exprType
    val curModCount = getManager.getModificationTracker.getModificationCount
    if (tp != null && exprTypeModCount == curModCount) {
      return tp
    }
    tp = valueType(ctx)
    exprType = tp
    exprTypeModCount = curModCount
    return tp
  }

  @volatile
  private var implicitParameters: Option[Seq[ScalaResolveResult]] = None
  @volatile
  private var exprType: TypeResult[ScType] = null
  @volatile
  private var exprAfterImplicitType: ExpressionTypeResult = null
  @volatile
  private var expectedTypesCache: Array[ScType] = null

  @volatile
  private var exprTypeModCount: Long = 0
  @volatile
  private var expectedTypesModCount: Long = 0
  @volatile
  private var exprTypeAfterImplicitModCount: Long = 0

  @volatile
  private var nonValueType: TypeResult[ScType] = null
  @volatile
  private var nonValueTypeModCount: Long = 0

  private def typeWithUnderscore(ctx: TypingContext): TypeResult[ScType] = {
    getText.indexOf("_") match {
      case -1 => innerType(ctx) //optimization
      case _ => {
        val unders = ScUnderScoreSectionUtil.underscores(this)
        if (unders.length == 0) innerType(ctx)
        else {
          new Success(new ScMethodType(valueType(ctx, true).getOrElse(Any),
            unders.map(u => Parameter("", u.getType(ctx).getOrElse(Any), false, false)), false, getProject,
            getResolveScope), Some(this))
        }
      }
    }
  }


  def findImplicitParameters: Option[Seq[ScalaResolveResult]] = {
    ProgressManager.checkCanceled
    var ip = implicitParameters
    val curModCount = getManager.getModificationTracker.getModificationCount
    if (ip != null && exprTypeModCount == curModCount) {
      return ip
    }
    getType(TypingContext.empty) //to update implicitParameters field
    return implicitParameters
  }

  private def valueType(ctx: TypingContext, fromUnderscoreSection: Boolean = false): TypeResult[ScType] = {
    val inner = if (!fromUnderscoreSection) getNonValueType(ctx) else innerType(ctx)
    var res = inner.getOrElse(return inner)
    val exp = expectedType //to avoid None.get
    //if (exp == Some(Unit)) return Success(Unit, Some(this))

    //let's update implicitParameters field
    res match {
      case t@ScTypePolymorphicType(ScMethodType(retType, params, impl), typeParams) if impl => {
        val s: ScSubstitutor = typeParams.foldLeft(ScSubstitutor.empty) {
          (subst: ScSubstitutor, tp: TypeParameter) =>
            subst.bindT((tp.name, ScalaPsiUtil.getPsiElementId(tp.ptp)),
              new ScUndefinedType(new ScTypeParameterType(tp.ptp, ScSubstitutor.empty)))
        }
        val exprs = new ArrayBuffer[Expression]
        val resolveResults = new ArrayBuffer[ScalaResolveResult]
        val iterator = params.iterator
        while (iterator.hasNext) {
          val param = iterator.next
          val paramType = s.subst(param.paramType) //we should do all of this with information known before
          val collector = new ImplicitParametersCollector(this, paramType)
          val results = collector.collect
          if (results.length == 1) {
            resolveResults += results(0)
            results(0) match {
              case ScalaResolveResult(patt: ScBindingPattern, subst) => {
                exprs += new Expression(subst.subst(patt.getType(TypingContext.empty).get))
              }
              case ScalaResolveResult(fun: ScFunction, subst) => {
                val funType = {
                  if (fun.parameters.length == 0 || fun.paramClauses.clauses.apply(0).isImplicit) {
                    subst.subst(fun.getType(TypingContext.empty).get) match {
                      case ScFunctionType(ret, _) => ret
                      case x => x
                    }
                  }
                  else subst.subst(fun.getType(TypingContext.empty).get)
                }
                exprs += new Expression(funType)
              }
            }
          } else {
            resolveResults += null
            exprs += new Expression(Any)
          }
        }
        implicitParameters = Some(resolveResults.toSeq)
        val subst = t.polymorphicTypeSubstitutor
        res = ScalaPsiUtil.localTypeInference(retType, params, exprs.toSeq, typeParams, subst)
      }
      case ScMethodType(retType, params, isImplicit) if isImplicit => {
        val resolveResults = new ArrayBuffer[ScalaResolveResult]
        val iterator = params.iterator
        while (iterator.hasNext) {
          val param = iterator.next
          val paramType = param.paramType //we should do all of this with information known before
          val collector = new ImplicitParametersCollector(this, paramType)
          val results = collector.collect
          if (results.length == 1) {
            resolveResults += results(0)
          } else {
            resolveResults += null
          }
        }
        implicitParameters = Some(resolveResults.toSeq)
      }
      case _ =>
    }

    res match {
      case ScMethodType(retType, params, impl) if impl => res = retType //todo: move upper
      case ScTypePolymorphicType(internal, typeParams) if exp != None => {
        def updateRes(expected: ScType) {
          res = ScalaPsiUtil.localTypeInference(internal, Seq(Parameter("", expected, false, false)),
              Seq(new Expression(ScalaPsiUtil.undefineSubstitutor(typeParams).subst(internal.inferValueType))),
            typeParams) //here should work in different way:
        }
        if (!fromUnderscoreSection) {
          updateRes(exp.get)
        } else {
          exp.get match {
            case ScFunctionType(retType, _) => updateRes(retType)
            case _ => //do not update res, we haven't expected type
          }
        }

      }
      case _ =>
    }
    Success(res.inferValueType, Some(this))
  }

  def getNonValueType(ctx: TypingContext): TypeResult[ScType] = {
    ProgressManager.checkCanceled
    if (ctx != TypingContext.empty) return typeWithUnderscore(ctx)
    var tp = nonValueType
    val curModCount = getManager.getModificationTracker.getModificationCount
    if (tp != null && nonValueTypeModCount == curModCount) {
      return tp
    }
    tp = typeWithUnderscore(ctx)
    nonValueType = tp
    nonValueTypeModCount = curModCount
    return tp
  }

  protected def innerType(ctx: TypingContext): TypeResult[ScType] =
    Failure(ScalaBundle.message("no.type.inferred", getText()), Some(this))

  /**
   * Returns all types in respect of implicit conversions (defined and default)
   */
  def allTypes: Seq[ScType] = {
    (getType(TypingContext.empty) match {
      case Success(t, _) => t :: getImplicitTypes
      case _ => getImplicitTypes
    })
  }

  def allTypesAndImports: List[(ScType, scala.collection.Set[ImportUsed])] = {
    def implicitTypesAndImports = {
      (for (t <- getImplicitTypes) yield (t, getImportsForImplicit(t)))
    }
    (getType(TypingContext.empty) match {
      case Success(t, _) => (t, Set[ImportUsed]()) :: implicitTypesAndImports
      case _ => implicitTypesAndImports
    })
  }

  /**
   * Some expression may be replaced only with another one
   */
  def replaceExpression(expr: ScExpression, removeParenthesis: Boolean): ScExpression = {
    val oldParent = getParent
    if (oldParent == null) throw new PsiInvalidElementAccessException(this)
    if (removeParenthesis && oldParent.isInstanceOf[ScParenthesisedExpr]) {
      return oldParent.asInstanceOf[ScExpression].replaceExpression(expr, true)
    }
    val newExpr: ScExpression = if (ScalaPsiUtil.needParentheses(this, expr)) {
      ScalaPsiElementFactory.createExpressionFromText("(" + expr.getText + ")", getManager)
    } else expr
    val parentNode = oldParent.getNode
    val newNode = newExpr.copy.getNode
    parentNode.replaceChild(this.getNode, newNode)
    return newNode.getPsi.asInstanceOf[ScExpression]
  }


  def expectedType: Option[ScType] = ExpectedTypes.expectedExprType(this)

  def expectedTypes: Array[ScType] = {
    var tp = expectedTypesCache
    val curModCount = getManager.getModificationTracker.getModificationCount
    if (tp != null && expectedTypesModCount == curModCount) {
      return tp
    }
    tp = ExpectedTypes.expectedExprTypes(this)
    expectedTypesCache = tp
    expectedTypesModCount = curModCount
    return tp
  }

  private[expr] def setExpectedTypes(tps: Array[ScType]) {
    expectedTypesCache = tps
    expectedTypesModCount = getManager.getModificationTracker.getModificationCount
  }

  def getImplicitConversions: (Seq[PsiNamedElement], Option[PsiElement]) = {
    val implicits: Seq[PsiNamedElement] = implicitMap.map(_._2)
    val implicitFunction: Option[PsiElement] = getParent match {
      case ref: ScReferenceExpression => {
        val resolve = ref.multiResolve(false)
        if (resolve.length == 1) {
          resolve.apply(0).asInstanceOf[ScalaResolveResult].implicitFunction
        } else None
      }
      case inf: ScInfixExpr if (inf.isLeftAssoc && this == inf.rOp) || (!inf.isLeftAssoc && this == inf.lOp) => {
        val resolve = inf.operation.multiResolve(false)
        if (resolve.length == 1) {
          resolve.apply(0).asInstanceOf[ScalaResolveResult].implicitFunction
        } else None
      }
      case call: ScMethodCall => None //todo:
      case gen: ScGenerator => None //todo:
      case _ => getTypeAfterImplicitConversion().implicitFunction
    }
    (implicits, implicitFunction)
  }
}

object ScExpression {
  case class ExpressionTypeResult(tr: TypeResult[ScType],
                                  importsUsed: scala.collection.Set[ImportUsed],
                                  implicitFunction: Option[PsiNamedElement])
}