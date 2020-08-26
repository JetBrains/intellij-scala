package org.jetbrains.plugins.scala
package codeInsight

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.util.text.StringUtil
import com.intellij.psi.PsiElement
import com.intellij.psi.util.PsiTreeUtil
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.psi.api.base.ScLiteral
import org.jetbrains.plugins.scala.lang.psi.api.base.literals.ScBooleanLiteral
import org.jetbrains.plugins.scala.lang.psi.api.expr._
import org.jetbrains.plugins.scala.lang.psi.api.expr.xml.ScXmlExpr
import org.jetbrains.plugins.scala.lang.psi.api.statements.params.ScParameter
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementFactory.createExpressionFromText
import org.jetbrains.plugins.scala.lang.psi.types.nonvalue.Parameter
import org.jetbrains.plugins.scala.lang.refactoring.util.ScalaNamesUtil

package object intention {

  def addNameToArgumentsFix(element: PsiElement, onlyBoolean: Boolean): Option[() => Unit] = {
    val argList: ScArgumentExprList = PsiTreeUtil.getParentOfType(element, classOf[ScArgumentExprList])
    if (argList == null || argList.isBraceArgs) return None
    val currentArg = argList.exprs.find { e =>
      PsiTreeUtil.isAncestor(e, element, /*strict =*/ false)
    }
    currentArg match {
      case None => return None
      case Some(assign: ScAssignment) if assign.isNamedParameter => return None
      case _ =>
    }

    def matchedParamsAfter(): collection.Seq[(ScExpression, Parameter)] = {
      val sortedMatchedArgs = argList.matchedParameters
        .filter(pair => argList.isAncestorOf(pair._1))
        .sortBy(_._1.getTextOffset)

      sortedMatchedArgs.dropWhile {
        case (e, _) => !PsiTreeUtil.isAncestor(e, element, /*strict =*/ false)
        case _ => true
      }
    }

    val argsAndMatchedParams = matchedParamsAfter()
    val hasRepeated = argsAndMatchedParams.exists {
      case (_, param) if param.isRepeated => true
      case _ => false
    }
    val allNamesDefined = argsAndMatchedParams.forall {
      case (_, param) => !StringUtil.isEmpty(param.name) && param.psiParam.exists(_.isInstanceOf[ScParameter])
    }
    val hasUnderscore = argsAndMatchedParams.exists {
      case (_: ScUnderscoreSection, _) => true
      case _ => false
    }

    if (hasRepeated || !allNamesDefined || hasUnderscore) None
    else {
      val doIt = () => {
        argsAndMatchedParams.foreach {
          case (_ childOf (a: ScAssignment), param) if a.leftExpression.textMatches(param.name) =>
          case (argExpr, param) =>
            if (!onlyBoolean || (onlyBoolean && param.paramType.isBoolean)) {
              inWriteAction {
                argExpr.replace(createExpressionFromText(param.name + " = " + argExpr.getText)(element.getManager))
              }
            }
          case _ =>
        }
      }
      Some(doIt)
    }
  }

  /**
    * The usages of this method need to be refactored to remove StringBuilder implementation
    */
  def analyzeMethodCallArgs(methodCallArgs: ScArgumentExprList, argsBuilder: StringBuilder): Unit = {
    if (methodCallArgs.exprs.length == 1) {
      methodCallArgs.exprs.head match {
        case _: ScLiteral | _: ScTuple | _: ScReferenceExpression | _: ScGenericCall | _: ScXmlExpr | _: ScMethodCall =>
          argsBuilder.replace(argsBuilder.length - 1, argsBuilder.length, "").replace(0, 1, "")
        case infix: ScInfixExpr if infix.getBaseExpr.isInstanceOf[ScUnderscoreSection] =>
          argsBuilder.insert(0, "(").append(")")
        case _ =>
      }
    }
  }

  def caretIsInRange(operation: ScReferenceExpression)
                    (implicit editor: Editor): Boolean = {
    val range = operation.nameId.getTextRange
    val offset = editor.getCaretModel.getOffset
    range.getStartOffset <= offset && offset <= range.getEndOffset
  }

  def negate(expression: ScExpression): String = expression match {
    case ScPrefixExpr(operation, operand) if operation.textMatches("!") =>
      val target = operand match {
        case ScParenthesisedExpr(scExpression) => scExpression
        case _ => operand
      }
      inWriteAction {
        expression.replaceExpression(
          createExpressionFromText(target.getText)(expression),
          removeParenthesis = true
        ).getText
      }
    case ScBooleanLiteral(value) => (!value).toString
    case _ =>
      val text = expression.getText
      val needParenthesis = expression match {
        case _: ScInfixExpr |
             _: ScPrefixExpr => true
        case _ => ScalaNamesUtil.isOpCharacter(text(0))
      }
      "!" + text.parenthesize(needParenthesis)
  }

}
