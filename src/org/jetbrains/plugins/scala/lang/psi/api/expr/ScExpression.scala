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
  self =>

  /**
   * This method returns real type, after using implicit conversions.
   * Second parameter to return is used imports for this conversion.
   */
  //todo: cache?
  def getTypeAfterImplicitConversion(expectedOption: Option[ScType] = expectedType): (TypeResult[ScType], scala.collection.Set[ImportUsed]) = {
    val tr = getType(TypingContext.empty)
    val expected = expectedOption.getOrElse(return (tr, Set.empty))
    val tp = tr.getOrElse(return (tr, Set.empty))
    if (tp.conforms(expected)) return (tr, Set.empty)
    val f = for ((typez, imports) <- allTypesAndImports if typez.conforms(expected)) yield (typez, getClazzForType(typez), imports)
    if (f.length == 1) return (Success(f(0)._1, Some(this)), f(0)._3)
    else if (f.length == 0) return (tr, Set.empty)
    else {
      var res = f(0)
      if (res._2 == None) return (tr, Set.empty)
      var i = 1
      while (i < f.length) {
        val pr = f(i)
        if (pr._1.equiv(res._1)) {
          //todo: there are serious overloading resolutions to implement it
        }
        else if (pr._2 != None) {
          if (pr._2.get.isInheritor(res._2.get, true)) res = pr
          else return (tr, Set.empty)
        } else return (tr, Set.empty)
        i += 1
      }
      return (Success(res._1, Some(this)), res._3)
    }
  }

  def getType(ctx: TypingContext): TypeResult[ScType] = {
    var tp = exprType
    val curModCount = getManager.getModificationTracker.getModificationCount
    if (tp != null && modCount == curModCount) {
      return tp
    }
    tp = typeWithUnderscore
    exprType = tp
    modCount = curModCount
    return tp
  }

  @volatile
  private var exprType: TypeResult[ScType] = null

  @volatile
  private var modCount: Long = 0

  private def typeWithUnderscore: TypeResult[ScType] = {
    getText.indexOf("_") match {
      case -1 => innerType(TypingContext.empty) //optimization
      case _ => {
        val unders = ScUnderScoreSectionUtil.underscores(this)
        if (unders.length == 0) innerType(TypingContext.empty)
        else {
          new Success(ScFunctionType(innerType(TypingContext.empty).getOrElse(Any),
            unders.map(_.getType(TypingContext.empty).getOrElse(Any))), Some(this)
)        }
      }
    }
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
}
