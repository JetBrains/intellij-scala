package org.jetbrains.plugins.scalaDirective.lang.completion

import com.intellij.codeInsight.AutoPopupController
import com.intellij.codeInsight.completion.CompletionConfidence
import com.intellij.codeInsight.editorActions.TypedHandlerDelegate
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.{PsiComment, PsiElement, PsiFile}
import com.intellij.util.ThreeState
import org.jetbrains.plugins.scala.extensions.{ObjectExt, PsiElementExt}
import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.psi.api.ScalaFile
import org.jetbrains.plugins.scalaDirective.lang.completion.ScalaDirectiveAutoPopupCompletionHandler.{condition, isEmptyDirectiveComment}

final class EnableAutoPopupInScalaDirectiveComment extends CompletionConfidence {
  override def shouldSkipAutopopup(contextElement: PsiElement, psiFile: PsiFile, offset: Int): ThreeState = contextElement match {
    case comment: PsiComment if isEmptyDirectiveComment(comment) =>
      ThreeState.NO
    case _ => ThreeState.UNSURE
  }
}

final class ScalaDirectiveAutoPopupCompletionHandler extends TypedHandlerDelegate {
  override def checkAutoPopup(charTyped: Char, project: Project, editor: Editor, file: PsiFile): TypedHandlerDelegate.Result =
    if (!file.is[ScalaFile] || charTyped != '>') super.checkAutoPopup(charTyped, project, editor, file)
    else {
      val offset = editor.getCaretModel.getOffset
      AutoPopupController.getInstance(project).autoPopupMemberLookup(editor, condition(offset)(_))
      TypedHandlerDelegate.Result.STOP
    }
}

object ScalaDirectiveAutoPopupCompletionHandler {
  private[completion] def isEmptyDirectiveComment(element: PsiElement): Boolean =
    element.is[PsiComment] &&
      element.elementType == ScalaTokenTypes.tLINE_COMMENT &&
      element.getText.trim == DirectivePrefix

  private def condition(offset: Int)(file: PsiFile): Boolean = {
    val leaf = file.findElementAt(offset)
    isEmptyDirectiveComment(leaf)
  }
}
