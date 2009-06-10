package org.jetbrains.plugins.scala.lang.psi.api.expr

import _root_.org.jetbrains.plugins.scala.lang.resolve.ScalaResolveResult
import base.patterns.ScCaseClause
import base.ScLiteral
import com.intellij.psi.codeStyle.CodeStyleSettingsManager
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
    if (removeParenthesis && oldParent.isInstanceOf[ScParenthesisedExpr]) {
      return oldParent.asInstanceOf[ScExpression].replaceExpression(expr, true)
    }
    val newExpr: ScExpression = if (needParentheses(oldParent, expr)) {
      ScalaPsiElementFactory.createExpressionFromText("(" + expr.getText + ")", getManager)
    } else expr
    val parentNode = oldParent.getNode
    val newNode = newExpr.copy.getNode
    parentNode.replaceChild(this.getNode, newNode)
    return newNode.getPsi.asInstanceOf[ScExpression]
  }


  def expectedType: Option[ScType] = ExpectedTypes.expectedExprType(this)

  private def needParentheses(parent: PsiElement, expr: ScExpression): Boolean = {
    (parent, expr) match { //true only for other cases
      case _ if !parent.isInstanceOf[ScExpression] => false
      case (_: ScTuple, _) => false
      case (_: ScReferenceExpression, _: ScBlockExpr) => false
      case (_: ScReferenceExpression, _: ScNewTemplateDefinition) if expr.getText.endsWith(")") ||
              expr.getText.endsWith("}") || expr.getText.endsWith("]") => false
      case (_: ScReferenceExpression, _: ScReferenceExpression) => false
      case (_: ScReferenceExpression, _: ScGenericCall) => false
      case (_: ScReferenceExpression, _: ScMethodCall) => false
      case (_: ScReferenceExpression, _: ScLiteral) => false
      case (_: ScReferenceExpression, _) if expr.getText == "_" => false
      case (_: ScReferenceExpression, _: ScTuple) => false
      case (_: ScReferenceExpression, _: ScXmlExpr) => false
      case (_: ScReferenceExpression, _) => true
      case (_: ScGenericCall, _: ScReferenceExpression) => false
      case (_: ScGenericCall, _: ScMethodCall) => false
      case (_: ScGenericCall, _: ScLiteral) => false
      case (_: ScGenericCall, _: ScTuple) => false
      case (_: ScGenericCall, _: ScXmlExpr) => false
      case (_: ScGenericCall, _) if expr.getText == "_" => false
      case (_: ScGenericCall, _: ScBlockExpr) => false
      case (_: ScGenericCall, _) => true
      case (_: ScMethodCall, _: ScReferenceExpression) => false
      case (_: ScMethodCall, _: ScMethodCall) => false
      case (_: ScMethodCall, _: ScGenericCall) => false
      case (_: ScMethodCall, _: ScLiteral) => false
      case (_: ScMethodCall, _) if expr.getText == "_" => false
      case (_: ScMethodCall, _: ScXmlExpr) => false
      case (_: ScMethodCall, _: ScTuple) => false
      case (_: ScMethodCall, _) => true
      case (_: ScUnderscoreSection, _: ScReferenceExpression) => false
      case (_: ScUnderscoreSection, _: ScMethodCall) => false
      case (_: ScUnderscoreSection, _: ScGenericCall) => false
      case (_: ScUnderscoreSection, _: ScLiteral) => false
      case (_: ScUnderscoreSection, _) if expr.getText == "_" => false
      case (_: ScUnderscoreSection, _: ScXmlExpr) => false
      case (_: ScUnderscoreSection, _: ScTuple) => false
      case (_: ScUnderscoreSection, _) => true
      case (_: ScBlock, _) => false
      case (_: ScPrefixExpr, _: ScBlockExpr) => false
      case (_: ScPrefixExpr, _: ScNewTemplateDefinition) => false
      case (_: ScPrefixExpr, _: ScUnderscoreSection) => false
      case (_: ScPrefixExpr, _: ScReferenceExpression) => false
      case (_: ScPrefixExpr, _: ScGenericCall) => false
      case (_: ScPrefixExpr, _: ScMethodCall) => false
      case (_: ScPrefixExpr, _: ScLiteral) => false
      case (_: ScPrefixExpr, _) if expr.getText == "_" => false
      case (_: ScPrefixExpr, _: ScTuple) => false
      case (_: ScPrefixExpr, _: ScXmlExpr) => false
      case (_: ScPrefixExpr, _) => true
      case (_: ScInfixExpr, _: ScBlockExpr) => false
      case (_: ScInfixExpr, _: ScNewTemplateDefinition) => false
      case (_: ScInfixExpr, _: ScUnderscoreSection) => false
      case (_: ScInfixExpr, _: ScReferenceExpression) => false
      case (_: ScInfixExpr, _: ScGenericCall) => false
      case (_: ScInfixExpr, _: ScMethodCall) => false
      case (_: ScInfixExpr, _: ScLiteral) => false
      case (_: ScInfixExpr, _) if expr.getText == "_" => false
      case (_: ScInfixExpr, _: ScTuple) => false
      case (_: ScInfixExpr, _: ScXmlExpr) => false
      case (_: ScInfixExpr, _: ScPrefixExpr) => false
      case (par: ScInfixExpr, child: ScInfixExpr) => {
        import ParserUtils._
        import InfixExpr._
        if (par.lOp == this) {
          val lid = par.operation.getText
          val rid = child.operation.getText
          if (priority(lid) < priority(rid)) true
          else if (priority(rid) < priority(lid)) false
          else if (associate(lid) != associate(rid)) true
          else if (associate(lid) == -1) true
          else false
        }
        else {
          val lid = child.operation.getText
          val rid = par.operation.getText
          if (priority(lid) < priority(rid)) false
          else if (priority(rid) < priority(lid)) true
          else if (associate(lid) != associate(rid)) true
          else if (associate(lid) == -1) false
          else true
        }
      }
      case (_: ScInfixExpr, _) => true
      case (_: ScPostfixExpr, _: ScBlockExpr) => false
      case (_: ScPostfixExpr, _: ScNewTemplateDefinition) => false
      case (_: ScPostfixExpr, _: ScUnderscoreSection) => false
      case (_: ScPostfixExpr, _: ScReferenceExpression) => false
      case (_: ScPostfixExpr, _: ScGenericCall) => false
      case (_: ScPostfixExpr, _: ScMethodCall) => false
      case (_: ScPostfixExpr, _: ScLiteral) => false
      case (_: ScPostfixExpr, _) if expr.getText == "_" => false
      case (_: ScPostfixExpr, _: ScTuple) => false
      case (_: ScPostfixExpr, _: ScXmlExpr) => false
      case (_: ScPostfixExpr, _: ScPrefixExpr) => false
      case (_: ScPostfixExpr, _) => true
      case (_: ScTypedStmt, _: ScBlockExpr) => false
      case (_: ScTypedStmt, _: ScNewTemplateDefinition) => false
      case (_: ScTypedStmt, _: ScUnderscoreSection) => false
      case (_: ScTypedStmt, _: ScReferenceExpression) => false
      case (_: ScTypedStmt, _: ScGenericCall) => false
      case (_: ScTypedStmt, _: ScMethodCall) => false
      case (_: ScTypedStmt, _: ScLiteral) => false
      case (_: ScTypedStmt, _) if expr.getText == "_" => false
      case (_: ScTypedStmt, _: ScTuple) => false
      case (_: ScTypedStmt, _: ScXmlExpr) => false
      case (_: ScTypedStmt, _: ScPrefixExpr) => false
      case (_: ScTypedStmt, _: ScInfixExpr) => false
      case (_: ScTypedStmt, _: ScPostfixExpr) => false
      case (_: ScTypedStmt, _) => true
      case (_: ScMatchStmt, _: ScBlockExpr) => false
      case (_: ScMatchStmt, _: ScNewTemplateDefinition) => false
      case (_: ScMatchStmt, _: ScUnderscoreSection) => false
      case (_: ScMatchStmt, _: ScReferenceExpression) => false
      case (_: ScMatchStmt, _: ScGenericCall) => false
      case (_: ScMatchStmt, _: ScMethodCall) => false
      case (_: ScMatchStmt, _: ScLiteral) => false
      case (_: ScMatchStmt, _) if expr.getText == "_" => false
      case (_: ScMatchStmt, _: ScTuple) => false
      case (_: ScMatchStmt, _: ScXmlExpr) => false
      case (_: ScMatchStmt, _: ScPrefixExpr) => false
      case (_: ScMatchStmt, _: ScInfixExpr) => false
      case (_: ScMatchStmt, _: ScPostfixExpr) => false
      case (_: ScMatchStmt, _) => true
      case _ => false
    }
  }
}