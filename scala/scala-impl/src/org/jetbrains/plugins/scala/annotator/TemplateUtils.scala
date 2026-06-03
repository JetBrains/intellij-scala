package org.jetbrains.plugins.scala.annotator

import com.intellij.codeInsight.template.{Template, TemplateManager}
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.fileEditor.{FileEditorManager, OpenFileDescriptor}
import com.intellij.psi.PsiElement
import org.jetbrains.plugins.scala.console.ScalaLanguageConsoleUtils

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

    val editor = originalEditor match {
      case Some(editor) if ScalaLanguageConsoleUtils.isConsole(editor) =>
        //for Scala REPL tool window we need to use the original editor
        //otherwise a new editor will be opened outside "Scala REPL" tool window (SCL-3750)
        editor.getCaretModel.moveToOffset(offset)
        editor
      case _ =>
        val virtualFile = element.getContainingFile.getVirtualFile
        val descriptor = new OpenFileDescriptor(project, virtualFile, offset)
        FileEditorManager.getInstance(project).openTextEditor(descriptor, true)
    }

    val range = element.getTextRange
    editor.getDocument.deleteString(range.getStartOffset, range.getEndOffset)
    TemplateManager.getInstance(project).startTemplate(editor, template)
  }
}
