package org.jetbrains.plugins.scalaDirective.lang.completion

import com.intellij.codeInsight.AutoPopupController
import com.intellij.codeInsight.completion.CompletionConfidence
import com.intellij.codeInsight.editorActions.TypedHandlerDelegate
import com.intellij.codeInsight.editorActions.TypedHandlerDelegate.Result
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.fileTypes.FileType
import com.intellij.openapi.project.Project
import com.intellij.psi.{PsiComment, PsiElement, PsiFile}
import com.intellij.util.ThreeState
import org.jetbrains.plugins.scala.extensions.{ObjectExt, PsiElementExt}
import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.psi.api.ScalaFile
import org.jetbrains.plugins.scalaDirective.dependencies.ScalaDirectiveDependencyPattern
import org.jetbrains.plugins.scalaDirective.lang.completion.ScalaDirectiveAutoPopupCompletionHandler._

final class EnableAutoPopupInScalaDirectiveComment extends CompletionConfidence {
  override def shouldSkipAutopopup(contextElement: PsiElement, psiFile: PsiFile, offset: Int): ThreeState = contextElement match {
    case comment: PsiComment if isEmptyDirectiveComment(comment) =>
      ThreeState.NO
    case _ => ThreeState.UNSURE
  }
}

final class ScalaDirectiveAutoPopupCompletionHandler extends TypedHandlerDelegate {

  override def checkAutoPopup(charTyped: Char, project: Project, editor: Editor, file: PsiFile): Result = {
    if (file.is[ScalaFile] && charTyped == '>') {
      scheduleAutoPopup(editor, project)(usingKeywordCondition)
    }
    Result.CONTINUE
  }

  override def beforeCharTyped(char: Char, project: Project, editor: Editor, file: PsiFile, fileType: FileType): Result = {
    if (file.is[ScalaFile] && char == ':') {
      scheduleAutoPopup(editor, project)(dependencyCondition)
    }
    Result.CONTINUE
  }
}

object ScalaDirectiveAutoPopupCompletionHandler {
  private def scheduleAutoPopup(editor: Editor, project: Project)(condition: Int => PsiFile => Boolean): Unit = {
    val offset = editor.getCaretModel.getOffset - 1
    AutoPopupController.getInstance(project).autoPopupMemberLookup(editor, condition(offset)(_))
  }

  private[completion] def isEmptyDirectiveComment(element: PsiElement): Boolean =
    element.is[PsiComment] &&
      element.elementType == ScalaTokenTypes.tLINE_COMMENT &&
      element.getText.trim == DirectivePrefix

  private def usingKeywordCondition(offset: Int)(file: PsiFile): Boolean = {
    val leaf = file.findElementAt(offset)
    isEmptyDirectiveComment(leaf)
  }

  private def dependencyCondition(offset: Int)(file: PsiFile): Boolean = {
    val leaf = file.findElementAt(offset)
    ScalaDirectiveDependencyPattern.accepts(leaf)
  }
}
