package org.jetbrains.plugins.scala
package actions

import _root_.com.intellij.codeInsight.TargetElementUtilBase
import _root_.com.intellij.psi._
import com.intellij.psi.util.{PsiTreeUtil, PsiUtilBase}
import _root_.org.jetbrains.plugins.scala.lang.psi.types.result.TypingContext
import com.intellij.openapi.actionSystem.{AnAction, AnActionEvent, CommonDataKeys}
import com.intellij.openapi.editor.Editor
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.psi.api.base.patterns.ScBindingPattern
import org.jetbrains.plugins.scala.lang.psi.api.base.{ScFieldId, ScPrimaryConstructor}
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScExpression
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScFunction
import org.jetbrains.plugins.scala.lang.psi.api.statements.params.ScParameter
import org.jetbrains.plugins.scala.lang.psi.types.{ScSubstitutor, ScType}
import org.jetbrains.plugins.scala.lang.refactoring.util.ScalaRefactoringUtil

/**
 * Pavel.Fatin, 16.04.2010
 */

class ShowTypeInfoAction extends AnAction(ScalaBundle.message("type.info")) {

  override def update(e: AnActionEvent) {
    ScalaActionUtil.enableAndShowIfInScalaFile(e)
  }

  def actionPerformed(e: AnActionEvent) {
    val context = e.getDataContext
    val editor = CommonDataKeys.EDITOR.getData(context)

    if(editor == null) return
    val file = PsiUtilBase.getPsiFileInEditor(editor, CommonDataKeys.PROJECT.getData(context))
    if (file.getLanguage != ScalaFileType.SCALA_LANGUAGE) return

    val selectionModel = editor.getSelectionModel
    if (selectionModel.hasSelection) {
      import selectionModel._

      def hintForPattern: Option[String] = {
        val pattern = PsiTreeUtil.findElementOfClassAtRange(file, getSelectionStart, getSelectionEnd, classOf[ScBindingPattern])
        ShowTypeInfoAction.typeInfoFromPattern(pattern).map("Type: " + _)
      }

      def hintForExpression: Option[String] = {
        val exprWithTypes: Option[(ScExpression, Array[ScType])] =
          ScalaRefactoringUtil.getExpression(file.getProject, editor, file, getSelectionStart, getSelectionEnd)

        exprWithTypes.map {
          case (expr, arr) if arr.nonEmpty =>
            val tpe = arr.head
            val tpeWithoutImplicits = expr.getTypeWithoutImplicits(TypingContext.empty).toOption
            val tpeWithoutImplicitsText = tpeWithoutImplicits.map(_.presentableText)

            val tpeText = tpe.presentableText
            val expectedTypeText = expr.expectedType().map(_.presentableText)

            (tpeWithoutImplicitsText, expectedTypeText) match {
              case (None, Some(expectedText)) =>
                """|Type: %s
                  |Expected Type: %s""".format(tpeText, expectedText).stripMargin
              case (None | Some(`tpeText`), None) => tpeText
              case (Some(originalTypeText), None) =>
                """|Type:  %s
                  |Original Type: %s""".format(tpeText, originalTypeText).stripMargin
              case (Some(withoutImplicitsText), Some(expectedText)) =>
                """|Type: %s
                  |Original Type: %s
                  |Expected Type: %s""".format(tpeText, withoutImplicitsText, expectedText).stripMargin
            }
          case _ => "Could not find type for selection"
        }
      }
      val hint = hintForPattern orElse hintForExpression
      hint.foreach(ScalaActionUtil.showHint(editor, _))

    } else {
      val offset = TargetElementUtilBase.adjustOffset(file, editor.getDocument,
        editor.logicalPositionToOffset(editor.getCaretModel.getLogicalPosition))
      ShowTypeInfoAction.getTypeInfoHint(editor, file, offset).foreach(ScalaActionUtil.showHint(editor, _))
    }
  }
}

object ShowTypeInfoAction {
  def getTypeInfoHint(editor: Editor, file: PsiFile, offset: Int): Option[String] = {
    val typeInfoFromRef = file.findReferenceAt(offset) match {
      case ResolvedWithSubst(e, subst) => typeOf(e, subst)
      case _ =>
        val element = file.findElementAt(offset)
        if (element == null) return None
        if (element.getNode.getElementType != ScalaTokenTypes.tIDENTIFIER) return None
        element match {
          case Parent(p) => typeOf(p, ScSubstitutor.empty)
          case _ => None
        }
    }
    val pattern = PsiTreeUtil.findElementOfClassAtOffset(file, offset, classOf[ScBindingPattern], false)
    typeInfoFromRef.orElse(typeInfoFromPattern(pattern))
  }

  def typeInfoFromPattern(p: ScBindingPattern): Option[String] = {
    p match {
      case null => None
      case _ => typeOf(p, ScSubstitutor.empty)
    }
  }

  val NO_TYPE: String = "No type was inferred"

  private[this] val typeOf: (PsiElement, ScSubstitutor) => Option[String] = {
    case (p: ScPrimaryConstructor, _) => None
    case (e: ScFunction, _) if e.isConstructor => None
    case (e: ScFunction, s) =>
      Some(e.returnType.toOption.map(s.subst).map(_.presentableText).getOrElse(NO_TYPE))
    case (e: ScBindingPattern, s) =>
      Some(e.getType(TypingContext.empty).toOption.map(s.subst).map(_ .presentableText).getOrElse(NO_TYPE))
    case (e: ScFieldId, s) =>
      Some(e.getType(TypingContext.empty).toOption.map(s.subst).map(_ .presentableText).getOrElse(NO_TYPE))
    case (e: ScParameter, s) =>
      Some(e.getRealParameterType(TypingContext.empty).toOption.map(s.subst).map(_ .presentableText).getOrElse(NO_TYPE))
    case (e: PsiMethod, _) if e.isConstructor => None
    case (e: PsiMethod, s) =>
      Some(e.getReturnType.toOption.map(p => s.subst(ScType.create(p, e.getProject, e.getResolveScope))).
        map(_.presentableText).getOrElse(NO_TYPE))
    case (e: PsiVariable, s) =>
      Some(e.getType.toOption.map(p => s.subst(ScType.create(p, e.getProject, e.getResolveScope))).
        map(_.presentableText).getOrElse(NO_TYPE))
    case _ => None
  }
}



