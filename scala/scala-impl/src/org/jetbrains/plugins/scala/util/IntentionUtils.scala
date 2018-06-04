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
import org.jetbrains.plugins.scala.actions.{ShowImplicitConversionsAction, MakeExplicitAction}
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.psi.api.base.ScLiteral
import org.jetbrains.plugins.scala.lang.psi.api.expr._
import org.jetbrains.plugins.scala.lang.psi.api.expr.xml.ScXmlExpr
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScFunction
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementFactory.{createExpressionFromText, createReferenceFromText}
import org.jetbrains.plugins.scala.lang.psi.types.nonvalue.Parameter
import org.jetbrains.plugins.scala.lang.refactoring.util.ScalaNamesUtil
import org.jetbrains.plugins.scala.project.ProjectContext

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
      val sortedMatchedArgs = argList.matchedParameters
        .filter(pair => argList.isAncestorOf(pair._1))
        .sortBy(_._1.getTextOffset)

      sortedMatchedArgs.dropWhile {
        case (e, _) => !PsiTreeUtil.isAncestor(e, element, /*strict =*/false)
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
      case (_: ScUnderscoreSection, _) => true
      case _ => false
    }

    if (hasRepeated || !allNamesDefined || hasUnderscore) None
    else {
      val doIt = () => {
        argsAndMatchedParams.foreach {
          case (_ childOf (a: ScAssignStmt), param) if a.getLExpression.getText == param.name =>
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
    * @param methodCallArgs
    * @param argsBuilder
    */
  def analyzeMethodCallArgs(methodCallArgs: ScArgumentExprList, argsBuilder: StringBuilder) {
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

  def negateAndValidateExpression(infix: ScInfixExpr, text: String)
                                 (implicit project: Project, editor: Editor): Unit = {
    val start = infix.getTextRange.getStartOffset
    val diff = editor.getCaretModel.getOffset - infix.operation.nameId.getTextRange.getStartOffset

    val (anchor, replacement, size) = negateAndValidateExpressionImpl(infix, text)

    inWriteAction {
      anchor.replaceExpression(replacement, removeParenthesis = true)
      editor.getCaretModel.moveToOffset(start + diff + size)
      PsiDocumentManager.getInstance(project).commitDocument(editor.getDocument)
    }
  }

  private def negateAndValidateExpressionImpl(infix: ScInfixExpr, text: String)
                                             (implicit context: ProjectContext): (ScExpression, ScExpression, Int) = {
    val parent = infix.getParent match {
      case p: ScParenthesisedExpr => p.getParent
      case p => p
    }

    parent match {
      case prefix: ScPrefixExpr if prefix.operation.getText == "!" =>
        val newExpr = createExpressionFromText(text)

        val size = newExpr match {
          case infix: ScInfixExpr => infix.operation.nameId.getTextRange.getStartOffset -
            newExpr.getTextRange.getStartOffset - 2
          case _ => 0
        }

        (parent.asInstanceOf[ScPrefixExpr], newExpr, size)
      case _ =>
        val newExpr = createExpressionFromText("!(" + text + ")")

        val children = newExpr.asInstanceOf[ScPrefixExpr].getLastChild.asInstanceOf[ScParenthesisedExpr].getChildren
        val size = children(0) match {
          case infix: ScInfixExpr => infix.operation.
            nameId.getTextRange.getStartOffset - newExpr.getTextRange.getStartOffset
          case _ => 0
        }
        (infix, newExpr, size)
    }
  }

  def negate(expression: ScExpression): String = {
    expression match {
      case e: ScPrefixExpr =>
        if (e.operation.getText == "!") {
          val exprWithoutParentheses =
            if (e.getBaseExpr.isInstanceOf[ScParenthesisedExpr]) e.getBaseExpr.getText.drop(1).dropRight(1)
            else e.getBaseExpr.getText
          inWriteAction {
            e.replaceExpression(createExpressionFromText(exprWithoutParentheses)(expression.getManager), removeParenthesis = true).getText
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
                          elements: Seq[PsiNamedElement]) {
    if (expr == null || f == null || elements == null) return
    CommandProcessor.getInstance().executeCommand(project, new Runnable {
      def run() {
        val buf = new StringBuilder
        val clazz = f.containingClass
        if (clazz != null && elements.contains(f)) buf.append(clazz.name).append(".")

        buf.append(f.name).append("(").append(expr.getText).append(")")
        inWriteAction {
          val replaced = expr.replace(createExpressionFromText(buf.toString())(expr.getManager))
          val ref = replaced.asInstanceOf[ScMethodCall].deepestInvokedExpr.asInstanceOf[ScReferenceExpression]
          val qualRef = ref.qualifier.orNull
          if (clazz != null && qualRef != null && elements.contains(f)) qualRef.asInstanceOf[ScReferenceExpression].bindToElement(clazz)
          PsiDocumentManager.getInstance(project).commitDocument(editor.getDocument)
        }
      }
    }, null, null)
  }

  def replaceWithExplicitStatically(expr: ScExpression, f: ScFunction, project: Project, editor: Editor,
                                    elements: Seq[PsiNamedElement]) {
    if (expr == null || f == null || elements == null) return
    CommandProcessor.getInstance().executeCommand(project, new Runnable {
      def run() {
        val buf = new StringBuilder
        val clazz = f.containingClass
        if (clazz != null && elements.contains(f)) buf.append(clazz.qualifiedName).append(".")

        val bufExpr = new StringBuilder
        bufExpr.append(f.name).append("(").append(expr.getText).append(")")
        buf.append(bufExpr.toString())

        implicit val projectContext = expr.projectContext
        val newExpr = createExpressionFromText(bufExpr.toString())
        val fullRef = createReferenceFromText(buf.toString()).resolve()

        inWriteAction {
          val replaced = expr.replace(newExpr)
          val ref = replaced.asInstanceOf[ScMethodCall].deepestInvokedExpr.asInstanceOf[ScReferenceExpression]
          if (clazz != null && fullRef != null && elements.contains(f)) ref.bindToElement(fullRef, Some(clazz))
          PsiDocumentManager.getInstance(project).commitDocument(editor.getDocument)
        }
      }
    }, null, null)
  }

  import JListCompatibility.GoToImplicitConversionAction.getList

  def showMakeExplicitPopup(project: Project, expr: ScExpression,
                            function: ScFunction,
                            editor: Editor,
                            elements: Seq[PsiNamedElement]): Unit = {
    val values = new ArrayBuffer[String]
    values += MakeExplicitAction.MAKE_EXPLICIT
    if (elements.contains(function)) values += MakeExplicitAction.MAKE_EXPLICIT_STATICALLY
    val base = new BaseListPopupStep[String](null, values.toArray : _*) {
      override def getTextFor(value: String): String = value

      override def onChosen(selectedValue: String, finalChoice: Boolean): PopupStep[_] = {
        if (selectedValue == null) return PopupStep.FINAL_CHOICE
        if (finalChoice) {
          PsiDocumentManager.getInstance(project).commitAllDocuments()
          ShowImplicitConversionsAction.getPopup.dispose()
          if (selectedValue == MakeExplicitAction.MAKE_EXPLICIT)
            IntentionUtils.replaceWithExplicit(expr, function, project, editor, elements)
          if (selectedValue == MakeExplicitAction.MAKE_EXPLICIT_STATICALLY)
            IntentionUtils.replaceWithExplicitStatically(expr, function, project, editor, elements)
          return PopupStep.FINAL_CHOICE
        }
        super.onChosen(selectedValue, finalChoice)
      }
    }

    val popup = JBPopupFactory.getInstance.createListPopup(base)
    val bounds: Rectangle = getCurrentItemBounds

    popup.show(new RelativePoint(getList, new Point(bounds.x + bounds.width - 20, bounds.y)))
  }

  def getCurrentItemBounds: Rectangle = {
    val list = getList
    list.getSelectedIndex match {
      case -1 => throw new RuntimeException("Index = -1 is less than zero.")
      case index =>
        list.getCellBounds(index, index) match {
          case null => throw new RuntimeException(s"No bounds for index = $index.")
          case result => result
        }
    }
  }

}
