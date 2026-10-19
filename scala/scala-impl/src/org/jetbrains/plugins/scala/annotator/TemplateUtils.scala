package org.jetbrains.plugins.scala.annotator

import com.intellij.codeInsight.template.{Template, TemplateManager}
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.fileEditor.{FileEditorManager, FileEditorOpenRequest, OpenFileDescriptor}
import com.intellij.psi.{PsiDocumentManager, PsiElement}
import org.jetbrains.plugins.scala.console.ScalaLanguageConsoleUtils
import org.jetbrains.plugins.scala.extensions.executeWriteActionCommand

object TemplateUtils {

  def startTemplateAtElement(
    editor: Editor,
    template: Template,
    element: PsiElement
  ): Unit = {
    val range = element.getTextRange
    editor.getCaretModel.moveToOffset(range.getStartOffset)
    editor.getDocument.deleteString(range.getStartOffset, range.getEndOffset)
    TemplateManager.getInstance(element.getProject).startTemplate(editor, template)
  }

  def positionCursorAndStartTemplate(
    element: PsiElement,
    template: Template,
    originalEditor: Editor
  ): Unit = {
    positionCursorAndStartTemplate(element, template, Some(originalEditor))
  }

  /**
   * The method should be used in quick fixes and actions which can potentially create elements in different files
   * In this case a new editor will be opened for that file and the `template` will be started there
   */
  def positionCursorAndStartTemplate(
    element: PsiElement,
    template: Template,
    originalEditor: Option[Editor]
  ): Unit = {
    val project = element.getProject
    val offset = element.getLastChild.getTextRange.getEndOffset

    def startTemplate(editor: Editor): Unit = {
      val range = element.getTextRange
      editor.getDocument.deleteString(range.getStartOffset, range.getEndOffset)
      TemplateManager.getInstance(project).startTemplate(editor, template)
    }

    def canUseOriginalEditor(editor: Editor): Boolean =
      ScalaLanguageConsoleUtils.isConsole(editor) ||
        editor.getDocument == PsiDocumentManager.getInstance(project).getDocument(element.getContainingFile)

    originalEditor match {
      case Some(editor) if canUseOriginalEditor(editor) =>
        // Use the current editor for its file and for the Scala REPL tool window (SCL-3750).
        editor.getCaretModel.moveToOffset(offset)
        startTemplate(editor)
      case _ =>
        val virtualFile = element.getContainingFile.getVirtualFile
        val descriptor = new OpenFileDescriptor(project, virtualFile, offset)
        FileEditorManager.getInstance(project)
          .requestOpenTextEditor(descriptor, FileEditorOpenRequest.withFocus(true))
          .thenAccept { editor =>
            if (editor != null) {
              executeWriteActionCommand()(startTemplate(editor))(using project)
            }
          }
    }
  }
}
