package org.jetbrains.plugins.scala
package util

import java.awt.{Point, Rectangle}

import com.intellij.openapi.command.CommandProcessor
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.popup.util.BaseListPopupStep
import com.intellij.openapi.ui.popup.{JBPopupFactory, PopupStep}
import com.intellij.openapi.util.text.StringUtil
import com.intellij.psi._
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.ui.awt.RelativePoint
import org.jetbrains.plugins.scala.actions.{GoToImplicitConversionAction, MakeExplicitAction}
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.psi.api.base.ScLiteral
import org.jetbrains.plugins.scala.lang.psi.api.expr._
import org.jetbrains.plugins.scala.lang.psi.api.expr.xml.ScXmlExpr
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScFunction
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementFactory
import org.jetbrains.plugins.scala.lang.psi.types.Boolean
import org.jetbrains.plugins.scala.lang.psi.types.nonvalue.Parameter
import org.jetbrains.plugins.scala.lang.refactoring.util.ScalaNamesUtil

import scala.collection.Seq
import scala.collection.mutable.ArrayBuffer

/**
 * @author Ksenia.Sautina
 * @since 5/12/12
 */

object IntentionUtils {

  def addNameToArgumentsFix(element: PsiElement, onlyBoolean: Boolean): Option[() => Unit] = {
    val argList: ScArgumentExprList = PsiTreeUtil.getParentOfType(element, classOf[ScArgumentExprList])
    if (argList == null || argList.isBraceArgs) return None
    val currentArg = argList.exprs.find { e =>
      PsiTreeUtil.isAncestor(e, element, /*strict =*/false)
    }
    currentArg match {
      case None => return None
      case Some(assign: ScAssignStmt) if assign.isNamedParameter => return None
      case _ =>
    }

    def matchedParamsAfter(): Seq[(ScExpression, Parameter)] = {
      val sortedMatchedArgs = argList.matchedParameters.sortBy(_._1.getTextOffset)
      sortedMatchedArgs.dropWhile {
        case (e, p) => !PsiTreeUtil.isAncestor(e, element, /*strict =*/false)
        case _ => true
      }
    }

    val argsAndMatchedParams = matchedParamsAfter()
    val hasRepeated = argsAndMatchedParams.exists {
      case (_, param) if param.isRepeated => true
      case _ => false
    }
    val allNamesDefined = argsAndMatchedParams.forall {
      case (_, param) => !StringUtil.isEmpty(param.name)
    }
    val hasUnderscore = argsAndMatchedParams.exists {
      case (underscore: ScUnderscoreSection, _) => true
      case _ => false
    }

    if (hasRepeated || !allNamesDefined || hasUnderscore) None
    else {
      val doIt = () => {
        argsAndMatchedParams.foreach {
          case (argExpr childOf (a: ScAssignStmt), param) if a.getLExpression.getText == param.name =>
          case (argExpr, param) =>
            if (!onlyBoolean || (onlyBoolean && param.paramType == Boolean)) {
              val newArgExpr = ScalaPsiElementFactory.createExpressionFromText(param.name + " = " + argExpr.getText, element.getManager)
              inWriteAction {
                argExpr.replace(newArgExpr)
              }
            }
          case _ =>
        }
      }
      Some(doIt)
    }
  }

  def analyzeMethodCallArgs(methodCallArgs: ScArgumentExprList, argsBuilder: scala.StringBuilder) {
    if (methodCallArgs.exprs.length == 1) {
      methodCallArgs.exprs.head match {
        case _: ScLiteral => argsBuilder.replace(argsBuilder.length - 1, argsBuilder.length, "").replace(0, 1, "")
        case _: ScTuple => argsBuilder.replace(argsBuilder.length - 1, argsBuilder.length, "").replace(0, 1, "")
        case _: ScReferenceExpression => argsBuilder.replace(argsBuilder.length - 1, argsBuilder.length, "").replace(0, 1, "")
        case _: ScGenericCall => argsBuilder.replace(argsBuilder.length - 1, argsBuilder.length, "").replace(0, 1, "")
        case _: ScXmlExpr => argsBuilder.replace(argsBuilder.length - 1, argsBuilder.length, "").replace(0, 1, "")
        case _: ScMethodCall => argsBuilder.replace(argsBuilder.length - 1, argsBuilder.length, "").replace(0, 1, "")
        case infix: ScInfixExpr if infix.getBaseExpr.isInstanceOf[ScUnderscoreSection] =>
          argsBuilder.insert(0, "(").append(")")
        case _ =>
      }
    }
  }

  def negateAndValidateExpression(expr: ScExpression, manager: PsiManager, buf: scala.StringBuilder) = {
    val parent =
      if (expr.getParent != null && expr.getParent.isInstanceOf[ScParenthesisedExpr]) expr.getParent.getParent
      else expr.getParent

    if (parent != null && parent.isInstanceOf[ScPrefixExpr] &&
            parent.asInstanceOf[ScPrefixExpr].operation.getText == "!") {

      val newExpr = ScalaPsiElementFactory.createExpressionFromText(buf.toString(), manager)

      val size = newExpr match {
        case infix: ScInfixExpr =>infix.operation.nameId.getTextRange.getStartOffset -
              newExpr.getTextRange.getStartOffset - 2
        case _ => 0
      }

      (parent.asInstanceOf[ScPrefixExpr], newExpr, size)
    } else {
      buf.insert(0, "!(").append(")")
      val newExpr = ScalaPsiElementFactory.createExpressionFromText(buf.toString(), manager)

      val children = newExpr.asInstanceOf[ScPrefixExpr].getLastChild.asInstanceOf[ScParenthesisedExpr].getChildren
      val size = children(0) match {
        case infix: ScInfixExpr => infix.operation.
              nameId.getTextRange.getStartOffset - newExpr.getTextRange.getStartOffset
        case _ => 0
      }
      (expr, newExpr, size)
    }
  }

  def negate(expression: ScExpression): String = {
    expression match {
      case e: ScPrefixExpr =>
        if (e.operation.getText == "!") {
          val exprWithoutParentheses =
            if (e.getBaseExpr.isInstanceOf[ScParenthesisedExpr]) e.getBaseExpr.getText.drop(1).dropRight(1)
            else e.getBaseExpr.getText
          val newExpr = ScalaPsiElementFactory.createExpressionFromText(exprWithoutParentheses, expression.getManager)
          inWriteAction {
            e.replaceExpression(newExpr, removeParenthesis = true).getText
          }
        }
        else "!(" + e.getText + ")"
      case e: ScLiteral =>
        if (e.getNode.getFirstChildNode.getElementType == ScalaTokenTypes.kTRUE) "false"
        else if (e.getNode.getFirstChildNode.getElementType == ScalaTokenTypes.kFALSE) "true"
        else "!" + e.getText
      case _ =>
        val exprText = expression.getText
        if (ScalaNamesUtil.isOpCharacter(exprText(0)) || expression.isInstanceOf[ScInfixExpr]) "!(" + exprText + ")"
        else "!" + expression.getText
    }
  }

  def replaceWithExplicit(expr: ScExpression, f: ScFunction, project: Project, editor: Editor,
                          secondPart: Seq[PsiNamedElement]) {
    if (expr == null || f == null || secondPart == null) return
    CommandProcessor.getInstance().executeCommand(project, new Runnable {
      def run() {
        val buf = new StringBuilder
        val clazz = f.containingClass
        if (clazz != null && secondPart.contains(f)) buf.append(clazz.name).append(".")

        buf.append(f.name).append("(").append(expr.getText).append(")")
        val newExpr = ScalaPsiElementFactory.createExpressionFromText(buf.toString(), expr.getManager)

        inWriteAction {
          val replaced = expr.replace(newExpr)
          val ref = replaced.asInstanceOf[ScMethodCall].deepestInvokedExpr.asInstanceOf[ScReferenceExpression]
          val qualRef = ref.qualifier.orNull
          if (clazz!= null && qualRef != null && secondPart.contains(f)) qualRef.asInstanceOf[ScReferenceExpression].bindToElement(clazz)
          PsiDocumentManager.getInstance(project).commitDocument(editor.getDocument)
        }
      }
    }, null, null)
  }

  def replaceWithExplicitStatically(expr: ScExpression, f: ScFunction, project: Project, editor: Editor,
                          secondPart: Seq[PsiNamedElement]) {
    if (expr == null || f == null || secondPart == null) return
    CommandProcessor.getInstance().executeCommand(project, new Runnable {
      def run() {
        val buf = new StringBuilder
        val clazz = f.containingClass
        if (clazz != null && secondPart.contains(f)) buf.append(clazz.qualifiedName).append(".")

        val bufExpr = new StringBuilder
        bufExpr.append(f.name).append("(").append(expr.getText).append(")")
        buf.append(bufExpr.toString())
        val newExpr = ScalaPsiElementFactory.createExpressionFromText(bufExpr.toString(), expr.getManager)
        val fullRef = ScalaPsiElementFactory.createReferenceFromText(buf.toString(), expr.getManager).resolve()

        inWriteAction {
          val replaced = expr.replace(newExpr)
          val ref = replaced.asInstanceOf[ScMethodCall].deepestInvokedExpr.asInstanceOf[ScReferenceExpression]
          if (clazz != null && fullRef != null && secondPart.contains(f)) ref.bindToElement(fullRef, Some(clazz))
          PsiDocumentManager.getInstance(project).commitDocument(editor.getDocument)
        }
      }
    }, null, null)
  }

  def showMakeExplicitPopup(project: Project, expr: ScExpression,
                            function: ScFunction, editor: Editor, secondPart: scala.Seq[PsiNamedElement],
                            getCurrentItemBounds: () => Rectangle) {
    val values = new ArrayBuffer[String]
    values += MakeExplicitAction.MAKE_EXPLICIT
    if (secondPart.contains(function)) values += MakeExplicitAction.MAKE_EXPLICIT_STATICALLY
    val base = new BaseListPopupStep[String](null, values.toArray : _*) {
      override def getTextFor(value: String): String = value

      override def onChosen(selectedValue: String, finalChoice: Boolean): PopupStep[_] = {
        if (selectedValue == null) return PopupStep.FINAL_CHOICE
        if (finalChoice) {
          PsiDocumentManager.getInstance(project).commitAllDocuments()
          GoToImplicitConversionAction.getPopup.dispose()
          if (selectedValue == MakeExplicitAction.MAKE_EXPLICIT)
            IntentionUtils.replaceWithExplicit(expr, function, project, editor, secondPart)
          if (selectedValue == MakeExplicitAction.MAKE_EXPLICIT_STATICALLY)
            IntentionUtils.replaceWithExplicitStatically(expr, function, project, editor, secondPart)
          return PopupStep.FINAL_CHOICE
        }
        super.onChosen(selectedValue, finalChoice)
      }
    }

    val popup = JBPopupFactory.getInstance.createListPopup(base)
    val bounds: Rectangle = getCurrentItemBounds()

    popup.show(new RelativePoint(GoToImplicitConversionAction.getList, new Point(bounds.x + bounds.width - 20, bounds.y)))
  }

}
