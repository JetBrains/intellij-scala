package org.jetbrains.plugins.scala
package util

import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementFactory
import com.intellij.psi.util.PsiTreeUtil
import collection.Seq
import lang.psi.api.base.ScLiteral
import lang.psi.api.expr.xml.ScXmlExpr
import lang.psi.api.expr._
import com.intellij.psi._
import lang.lexer.ScalaTokenTypes
import lang.refactoring.util.ScalaNamesUtil
import lang.psi.api.statements.ScFunction
import com.intellij.openapi.project.Project
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.command.CommandProcessor
import scala.Some
import lang.psi.types.nonvalue.Parameter
import java.awt.{Point, Rectangle}
import collection.mutable.ArrayBuffer
import org.jetbrains.plugins.scala.actions.{GoToImplicitConversionAction, MakeExplicitAction}
import com.intellij.openapi.ui.popup.util.BaseListPopupStep
import com.intellij.openapi.ui.popup.{JBPopupFactory, PopupStep}
import com.intellij.ui.awt.RelativePoint
import lang.psi.types.Boolean

/**
 * @author Ksenia.Sautina
 * @since 5/12/12
 */

object IntentionUtils {

  def check(element: PsiElement, onlyBoolean: Boolean): Option[() => Unit] = {
    val containingArgList: Option[ScArgumentExprList] = element.parents.collectFirst {
      case al: ScArgumentExprList if !al.isBraceArgs => al
    }
    containingArgList match {
      case Some(al) =>
        val index = al.exprs.indexWhere(argExpr => PsiTreeUtil.isAncestor(argExpr, element, false))
        index match {
          case -1 => None
          case i =>
            val argExprsToNamify = al.exprs.drop(index)
            val argsAndMatchingParams: Seq[(ScExpression, Option[Parameter])] = argExprsToNamify.map {
              arg => (arg, al.parameterOf(arg))
            }
            val isRepeated = argsAndMatchingParams.exists {
              case (_, Some(param)) if param.isRepeated => true
              case _ => false
            }
            val hasName = argsAndMatchingParams.exists {
              case (_, Some(param)) if (!param.name.isEmpty) => true
              case _ => false
            }
            val hasUnderscore = argsAndMatchingParams.exists {
              case (_, Some(param)) if (param.isInstanceOf[ScUnderscoreSection]) => true
              case (underscore: ScUnderscoreSection, Some(param)) => true
              case param: ScUnderscoreSection => true
              case _ => false
            }
            argsAndMatchingParams.headOption match {
              case _ if isRepeated => None
              case _ if !hasName => None
              case _ if hasUnderscore => None
              case Some((assign: ScAssignStmt, Some(param))) if assign.getLExpression.getText == param.name =>
                None
              case None | Some((_, None)) =>
                None
              case _ =>
                val doIt = () => {
                  argsAndMatchingParams.foreach {
                    case (argExpr: ScAssignStmt, Some(param)) if argExpr.getLExpression.getText == param.name =>
                    case (argExpr, Some(param)) =>
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
      case None => None
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
        case infix: ScInfixExpr if (infix.getBaseExpr.isInstanceOf[ScUnderscoreSection]) =>
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
            e.replaceExpression(newExpr, true).getText
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
          val qualRef = ref.qualifier.getOrElse(null)
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
    val base = new BaseListPopupStep[String](null, values.toArray) {
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
