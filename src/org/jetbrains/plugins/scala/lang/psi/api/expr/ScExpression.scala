package org.jetbrains.plugins.scala.lang.psi.api.expr

import _root_.org.jetbrains.plugins.scala.lang.resolve.ScalaResolveResult
import base.patterns.ScCaseClause
import base.ScLiteral
import caches.CachesUtil
import com.intellij.psi.codeStyle.CodeStyleSettingsManager
import com.intellij.psi.util.PsiModificationTracker
import com.intellij.psi.{PsiElement, PsiInvalidElementAccessException}
import formatting.settings.ScalaCodeStyleSettings
import impl.ScalaPsiElementFactory
import implicits.{ScImplicitlyConvertible, Implicits}
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiElement
import parser.parsing.expressions.InfixExpr
import parser.util.ParserUtils
import statements.params.ScArguments
import statements.ScFunction
import types.{ScType, Nothing, ScFunctionType}
import xml.ScXmlExpr

/**
 * @author ilyas, Alexander Podkhalyuzin
 */

trait ScExpression extends ScBlockStatement with ScImplicitlyConvertible {
  self =>
  def getType: ScType = Nothing //todo

  def cachedType: ScType = {
    CachesUtil.get(
      this, CachesUtil.EXPR_TYPE_KEY,
      new CachesUtil.MyProvider(this, {ic: ScExpression => ic.getType})
        (PsiModificationTracker.MODIFICATION_COUNT)
    )
  }

  /**
   * Returns all types in respect of implicit conversions (defined and default)
   */
  def allTypes: Seq[ScType] = {
    cachedType :: getImplicitTypes
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