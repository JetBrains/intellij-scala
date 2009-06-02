package org.jetbrains.plugins.scala.lang.psi.api.expr

import _root_.org.jetbrains.plugins.scala.lang.resolve.ScalaResolveResult
import base.patterns.ScCaseClause
import com.intellij.psi.codeStyle.CodeStyleSettingsManager
import formatting.settings.ScalaCodeStyleSettings
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

trait ScExpression extends ScBlockStatement with ScImplicitlyConvertible {
  self =>
  def getType(): ScType = Nothing //todo

  /**
   * Returns all types in respect of implicit conversions (defined and default)
   */
  def allTypes: Seq[ScType] = {
    val settings: ScalaCodeStyleSettings =
      CodeStyleSettingsManager.getSettings(getProject).getCustomSettings(classOf[ScalaCodeStyleSettings])
    if (settings.CHECK_IMPLICITS)
      getType :: collectImplicitTypes
    else
      Seq[ScType](getType)
  }

  /**
   * Some expression may be replaced only with another one
   */
  def replaceExpression(expr: ScExpression, removeParenthesis: Boolean): ScExpression = {
    val oldParent = getParent
    if (oldParent == null) throw new PsiInvalidElementAccessException(this)
    //todo: implement checking priority (for inline refactoring)
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