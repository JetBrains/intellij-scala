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