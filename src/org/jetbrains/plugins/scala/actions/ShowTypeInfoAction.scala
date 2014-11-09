package org.jetbrains.plugins.scala
package actions

import _root_.com.intellij.codeInsight.TargetElementUtilBase
import _root_.com.intellij.psi._
import _root_.com.intellij.psi.util.PsiUtilBase
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
      val a: Option[(ScExpression, Array[ScType])] = ScalaRefactoringUtil.getExpression(file.getProject, editor, file, getSelectionStart, getSelectionEnd)
      a.foreach {
        case (expr, arr) if arr.nonEmpty =>
          val tpe = arr.head
          val tpeWithoutImplicits = expr.getTypeWithoutImplicits(TypingContext.empty).toOption
          val tpeWithoutImplicitsText = tpeWithoutImplicits.map(_.presentableText)

          val tpeText = tpe.presentableText
          val expectedTypeText = expr.expectedType().map(_.presentableText)

          val hint = (tpeWithoutImplicitsText, expectedTypeText) match {
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

          ScalaActionUtil.showHint(editor, hint)
        case _ => ScalaActionUtil.showHint(editor, "Could not find type for selection")
      }
    } else {
      val offset = TargetElementUtilBase.adjustOffset(file, editor.getDocument,
        editor.logicalPositionToOffset(editor.getCaretModel.getLogicalPosition))
      ShowTypeInfoAction.getTypeInfoHint(editor, file, offset).foreach(ScalaActionUtil.showHint(editor, _))
    }
  }
}

object ShowTypeInfoAction {
  def getTypeInfoHint(editor: Editor, file: PsiFile, offset: Int): Option[String] = {
    file.findReferenceAt(offset) match {
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
  }

  private[this] val typeOf: (PsiElement, ScSubstitutor) => Option[String] = {
    case (p: ScPrimaryConstructor, _) => None
    case (e: ScFunction, _) if e.isConstructor => None
    case (e: ScFunction, s) => e.returnType.toOption.map(s.subst).map(_.presentableText)
    case (e: ScBindingPattern, s) => e.getType(TypingContext.empty).toOption.map(s.subst).map(_ .presentableText)
    case (e: ScFieldId, s) => e.getType(TypingContext.empty).toOption.map(s.subst).map(_ .presentableText)
    case (e: ScParameter, s) => e.getRealParameterType(TypingContext.empty).toOption.map(s.subst).map(_ .presentableText)
    case (e: PsiMethod, _) if e.isConstructor => None
    case (e: PsiMethod, s) => e.getReturnType.toOption.
            map(p => s.subst(ScType.create(p, e.getProject, e.getResolveScope))).map(_ presentableText)
    case (e: PsiVariable, s) => e.getType.toOption.
            map(p => s.subst(ScType.create(p, e.getProject, e.getResolveScope))).map(_ presentableText)
    case _ => None
  }
}



