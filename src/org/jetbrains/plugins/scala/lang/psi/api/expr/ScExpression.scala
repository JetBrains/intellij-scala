package org.jetbrains.plugins.scala.lang.psi.api.expr

import _root_.org.jetbrains.plugins.scala.lang.resolve.ScalaResolveResult
import base.patterns.ScCaseClause
import impl.ScalaPsiElementFactory
import com.intellij.psi.PsiInvalidElementAccessException
import implicits.{ScImplicitlyConvertible, Implicits}
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiElement
import statements.params.ScArguments
import statements.ScFunction
import types.{ScType, Nothing, ScFunctionType}

/**
 * @author ilyas, Alexander Podkhalyuzin
 */

trait ScExpression extends ScBlockStatement /*with ScImplicitlyConvertible*/ {
  self =>
  def getType(): ScType = Nothing //todo

  /**
   * Returns all types in respect of implicit conversions (defined and default)
   */
  def allTypes: Seq[ScType] = getType :: List(Implicits.get(getType) : _*)

  /**
   * Some expression may be replaced only with another one
   */
  def replaceExpression(expr: ScExpression, removeParenthesis: Boolean): ScExpression = {
    val oldParent = getParent
    if (oldParent == null) throw new PsiInvalidElementAccessException(this)
    //todo: implement checking priority (when inline refactoring)
    if (removeParenthesis && oldParent.isInstanceOf[ScParenthesisedExpr]) {
      return oldParent.asInstanceOf[ScExpression].replaceExpression(expr, true)
    }
    val parentNode = oldParent.getNode
    val newNode = expr.copy.getNode
    parentNode.replaceChild(this.getNode, newNode)
    return newNode.getPsi.asInstanceOf[ScExpression]
  }


  def expectedType: Option[ScType] = ExpectedTypes.expectedExprType(this)
}