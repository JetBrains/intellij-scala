package org.jetbrains.plugins.scala
package lang
package psi
package api
package expr

import com.intellij.psi.{PsiInvalidElementAccessException}
import impl.ScalaPsiElementFactory
import implicits.{ScImplicitlyConvertible}
import types._
import types.result.{Success, Failure, TypingContext, TypeResult}
import toplevel.imports.usages.ImportUsed

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

      val tr = if (exp != None) { //save data
        val z = (expectedTypesCache, expectedTypesModCount, exprType, exprTypeModCount)
        var trData: TypeResult[ScType] = null
        try {
          expectedTypesCache = expectedOption.toList.toArray
          expectedTypesModCount = getManager.getModificationTracker.getModificationCount
          exprType = null
          trData = getType(TypingContext.empty)
        }
        finally {
          //load data
          expectedTypesCache = z._1;expectedTypesModCount = z._2;exprType = z._3;exprTypeModCount = z._4
        }
        trData
      } else getType(TypingContext.empty)

      val defaultResult: (TypeResult[ScType], scala.collection.Set[ImportUsed]) = (tr, Set.empty)
      val tp = tr.getOrElse(return defaultResult)
      //if this result is ok, we do not need to think about implicits
      if (tp.conforms(expected)) return defaultResult
      if (!checkImplicits) return defaultResult

      //this functionality for checking if this expression can be implicitly changed and then
      //it will conform to expected type
      val f = for ((typez, imports) <- allTypesAndImports if typez.conforms(expected)) yield (typez, getClazzForType(typez), imports)
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
    tp = typeWithUnderscore(ctx)
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
      case -1 => valueType(ctx) //optimization
      case _ => {
        val unders = ScUnderScoreSectionUtil.underscores(this)
        if (unders.length == 0) valueType(ctx)
        else {
          new Success(new ScFunctionType(valueType(ctx).getOrElse(Any),
            unders.map(_.getType(ctx).getOrElse(Any)), getProject), Some(this)
)        }
      }
    }
  }

  private def valueType(ctx: TypingContext): TypeResult[ScType] = {
    val inner = innerType(ctx)
    Success(inner.getOrElse(return inner).inferValueType, Some(this))
  }

  def getNonValueType(ctx: TypingContext): TypeResult[ScType] = {
    if (ctx != TypingContext.empty) return innerType(ctx)
    var tp = nonValueType
    val curModCount = getManager.getModificationTracker.getModificationCount
    if (tp != null && nonValueTypeModCount == curModCount) {
      return tp
    }
    tp = innerType(ctx)
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
}
