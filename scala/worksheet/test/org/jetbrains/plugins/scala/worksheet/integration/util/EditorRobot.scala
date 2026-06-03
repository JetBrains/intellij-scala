package org.jetbrains.plugins.scala.worksheet.integration.util

import com.intellij.openapi.editor.{Caret, Editor}
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.project.Project
import org.jetbrains.plugins.scala.editor.DocumentExt
import org.jetbrains.plugins.scala.extensions.inWriteCommandAction

class EditorRobot(editor: Editor) {

  private implicit val project: Project = editor.getProject

  private val document = editor.getDocument

  private def currentCaret: Caret = editor.getCaretModel.getCurrentCaret

  def moveToEnd(): Unit =
    currentCaret.moveToOffset(document.getTextLength)

  def pressEnter(): Unit =
    typeString("\n")

  def typeString(string: String): Unit =
    inWriteCommandAction {
      document.insertString(currentCaret.getOffset, string)
      FileDocumentManager.getInstance().saveDocumentAsIs(document)
      document.commit(project)
    }
}
