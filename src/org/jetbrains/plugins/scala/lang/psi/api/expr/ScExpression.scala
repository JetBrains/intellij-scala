package org.jetbrains.plugins.scala
package lang
package psi
package api
package expr

import com.intellij.psi.{PsiInvalidElementAccessException}
import impl.ScalaPsiElementFactory
import types._
import nonvalue.{TypeParameter, ScMethodType, Parameter, ScTypePolymorphicType}
import types.result.{Success, Failure, TypingContext, TypeResult}
import toplevel.imports.usages.ImportUsed
import types.Compatibility.Expression
import statements.ScFunction
import base.patterns.ScBindingPattern
import resolve.ScalaResolveResult
import implicits.{ImplicitParametersCollector, ScImplicitlyConvertible}
import collection.mutable.ArrayBuffer
import statements.params.ScParameter
import psi.{ScalaPsiUtil}

/**
 * @author ilyas, Alexander Podkhalyuzin
 */

trait ScExpression extends ScBlockStatement with ScImplicitlyConvertible {
  /**
   * This method returns real type, after using implicit conversions.
   * Second parameter to return is used imports for this conversion.
   * @param expectedOption to which type we tring to convert
   */
  def getTypeAfterImplicitConversion(exp: Option[Option[ScType]] = None, checkImplicits: Boolean = true):
    (TypeResult[ScType], scala.collection.Set[ImportUsed]) = {
    val expectedOption = exp match {
      case Some(a) => a
      case _ => expectedType
    }
    def inner: (TypeResult[ScType], scala.collection.Set[ImportUsed]) = {
      val expected: ScType = expectedOption match {
        case Some(a) => a
        case _ => return (getType(TypingContext.empty), Set.empty)
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

      def forExpr(expr: ScExpression): (TypeResult[ScType], scala.collection.Set[ImportUsed]) = {
        val tr = expr.getType(TypingContext.empty)
        val defaultResult: (TypeResult[ScType], scala.collection.Set[ImportUsed]) = (tr, Set.empty)
        val tp = tr.getOrElse(return defaultResult)
        //if this result is ok, we do not need to think about implicits
        if (tp.conforms(expected)) return defaultResult
        if (!checkImplicits) return defaultResult

        //this functionality for checking if this expression can be implicitly changed and then
        //it will conform to expected type
        val f = for ((typez, imports) <- expr.allTypesAndImports if typez.conforms(expected)) yield (typez, getClazzForType(typez), imports)
        if (f.length == 1) return (Success(f(0)._1, Some(this)), f(0)._3)
        else if (f.length == 0) return defaultResult
        else {
          var res = f(0)
          if (res._2 == None) return defaultResult
          var i = 1
          while (i < f.length) {
            val pr = f(i)
            if (pr._1.equiv(res._1)) {
              //todo: there are serious overloading resolutions to implement it
            }
            else if (pr._2 != None) {
              if (pr._2.get.isInheritor(res._2.get, true)) res = pr
              else return defaultResult
            } else return defaultResult
            i += 1
          }
          return (Success(res._1, Some(this)), res._3)
        }
      }

      if (exp != None) { //save data
        val z = (expectedTypesCache, expectedTypesModCount, exprType, exprTypeModCount)
        try {
          expectedTypesCache = expectedOption.toList.toArray
          expectedTypesModCount = getManager.getModificationTracker.getModificationCount
          exprType = null
          if (!anon(this)) forExpr(this) else {
            val newExpr = ScalaPsiElementFactory.createExpressionWithContextFromText(getText, getContext)
            newExpr.setExpectedTypes(expectedOption.toList.toArray)
            forExpr(newExpr)
          }
        }
        finally {
          //load data
          expectedTypesCache = z._1;expectedTypesModCount = z._2;exprType = z._3;exprTypeModCount = z._4
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

  def getType(ctx: TypingContext): TypeResult[ScType] = {
    if (ctx != TypingContext.empty) return typeWithUnderscore(ctx)
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
  private var exprType: TypeResult[ScType] = null
  @volatile
  private var exprAfterImplicitType: (TypeResult[ScType], scala.collection.Set[ImportUsed]) = null
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
            unders.map(u => Parameter("", u.getType(ctx).getOrElse(Any), false, false)), false), Some(this))
        }
      }
    }
  }

  private def valueType(ctx: TypingContext, fromUnderscoreSection: Boolean = false): TypeResult[ScType] = {
    val inner = if (!fromUnderscoreSection) getNonValueType(ctx) else innerType(ctx)
    var res = inner.getOrElse(return inner)
    res match {
      case t@ScTypePolymorphicType(ScMethodType(retType, params, impl), typeParams) if impl => {
        val s: ScSubstitutor = typeParams.foldLeft(ScSubstitutor.empty) {
          (subst: ScSubstitutor, tp: TypeParameter) =>
            subst.bindT(tp.name, new ScUndefinedType(new ScTypeParameterType(tp.ptp, ScSubstitutor.empty)))
        }
        val exprs = new ArrayBuffer[Expression]
        val iterator = params.iterator
        while (iterator.hasNext) {
          val param = iterator.next
          val paramType = s.subst(param.paramType) //we should do all of this with information known before
          val collector = new ImplicitParametersCollector(this, paramType)
          val results = collector.collect
          if (results.length == 1) {
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
          } else exprs += new Expression(Any)
        }
        val subst = t.polymorphicTypeSubstitutor
        res = ScalaPsiUtil.localTypeInference(retType, params, exprs.toSeq, typeParams, subst)
      }
      case _ =>
    }

    val exp = expectedType //to avoid None.get

    res match {
      case ScMethodType(retType, params, impl) if impl => res = retType
      case ScTypePolymorphicType(internal, typeParams) if exp != None => {
        def updateRes(expected: ScType) {
          res = ScalaPsiUtil.localTypeInference(internal, Seq(Parameter("", internal.inferValueType, false, false)),
              Seq(new Expression(expected)), typeParams)
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
}
