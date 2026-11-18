package org.jetbrains.plugins.scala.editor.typedHandler

import com.intellij.codeInsight.AutoPopupController
import com.intellij.codeInsight.completion.NewRdCompletionSupport
import com.intellij.codeInsight.editorActions.TypedHandlerDelegate.Result
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.editor.{Document, Editor}
import com.intellij.openapi.project.Project
import com.intellij.psi.codeStyle.CodeStyleSettings
import com.intellij.psi.{PsiElement, PsiFile}
import org.jetbrains.annotations.NotNull
import org.jetbrains.plugins.scala.editor.indentKeyword
import org.jetbrains.plugins.scala.extensions.ObjectExt
import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.psi.api.ScalaFile
import org.jetbrains.plugins.scala.lang.psi.api.base.patterns.ScCaseClause

/**
 * The part of [[ScalaTypedHandler]] that needs a resolve, a type or the backend completion.
 * This default is registered to make [[ScalaTypedHandler]] available on the client.
 */
//noinspection ApiStatus,JetBrainsInternalApiUsage
class ScalaTypingSupport {
  def checkAutoPopup(charTyped: Char, project: Project, editor: Editor, file: PsiFile): Result =
    if (NewRdCompletionSupport.Companion.isFrontendRdCompletionOn(editor)) if (!file.is[ScalaFile]) Result.CONTINUE
    else if (charTyped == '{' || charTyped == '[') {
      AutoPopupController.getInstance(project).autoPopupParameterInfo(editor, null)
      Result.STOP
    } else Result.CONTINUE else Result.CONTINUE

  def scheduleAutoPopup(file: PsiFile, editor: Editor, project: Project): Unit =
    if (NewRdCompletionSupport.Companion.isFrontendRdCompletionOn(editor)) doScheduleAutoPopup(file, editor, project)

  def startAutoPopupCompletion(file: PsiFile, editor: Editor)
                              (document: Document, project: Project, element: PsiElement, offset: Int): Unit = {}

  def startAutoPopupCompletionInInterpolatedString(file: PsiFile, editor: Editor)
                                                  (document: Document, project: Project, element: PsiElement, offset: Int): Unit = {}

  def handleLeftBraceWrap(caretOffset: Int, element: PsiElement)
                         (using project: Project, file: PsiFile, editor: Editor, settings: CodeStyleSettings) = false

  def indentCaseKeyword(file: PsiFile)(document: Document, project: Project, element: PsiElement, offset: Int): Unit =
    indentKeyword[ScCaseClause](ScalaTokenTypes.kCASE, file)(document, project, element, offset)

  def handleAutoBraces(char: Char, offset: Int, @NotNull element: PsiElement)
                      (using project: Project, file: PsiFile, editor: Editor) = false

  def handleAutoBracesWhenStartingStatement(char: Char, offset: Int, @NotNull element: PsiElement)
                                           (using project: Project, file: PsiFile, editor: Editor) = false

  protected def doScheduleAutoPopup(file: PsiFile, editor: Editor, project: Project): Unit =
    AutoPopupController.getInstance(project).scheduleAutoPopup(editor, (f: PsiFile) => f == file)
}

object ScalaTypingSupport {
  def getInstance(): ScalaTypingSupport = ApplicationManager.getApplication.getService(classOf[ScalaTypingSupport])
}
