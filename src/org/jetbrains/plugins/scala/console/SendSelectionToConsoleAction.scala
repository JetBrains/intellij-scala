package org.jetbrains.plugins.scala.console

import com.intellij.openapi.actionSystem._
import org.jetbrains.plugins.scala.lang.psi.api.ScalaFile
import org.jetbrains.plugins.scala.icons.Icons
import org.jetbrains.plugins.scala.extensions
import com.intellij.openapi.util.TextRange
import java.io.{IOException, OutputStream}

/**
 * @author Ksenia.Sautina
 * @since 7/25/12
 */

class SendSelectionToConsoleAction extends AnAction {

  override def update(e: AnActionEvent) {
    val presentation = e.getPresentation
    presentation.setIcon(Icons.SCALA_CONSOLE)

    def enable() {
      presentation.setEnabled(true)
      presentation.setVisible(true)
    }

    def disable() {
      presentation.setEnabled(false)
      presentation.setVisible(false)
    }

    try {
      val context = e.getDataContext
      val file = CommonDataKeys.PSI_FILE.getData(context)
      if (file == null) {
        disable()
        return
      }
      val editor = CommonDataKeys.EDITOR.getData(context)
      val hasSelection = editor.getSelectionModel.hasSelection
      val console = ScalaConsoleInfo.getConsole(file.getProject)

      if (!hasSelection || console == null) {
        disable()
        return
      }

      val consoleEditor = console.getConsoleEditor
      if (consoleEditor == null || consoleEditor.isDisposed) {
        disable()
        return
      }

      val processHandler = ScalaConsoleInfo.getProcessHandler(file.getProject)
      if (processHandler == null || processHandler.isProcessTerminated) {
        disable()
        return
      }

      file match {
        case _: ScalaFile => enable()
        case _ => disable()
      }
    }
    catch {
      case e: Exception => disable()
    }
  }

  def actionPerformed(e: AnActionEvent) {
    val context = e.getDataContext
    val editor = CommonDataKeys.EDITOR.getData(context)
    val project = CommonDataKeys.PROJECT.getData(context)

    if (editor == null || project == null) return
    val selectedText = editor.getSelectionModel.getSelectedText
    val console = ScalaConsoleInfo.getConsole(project)
    if (console != null) sendSelection(console, selectedText)
  }

  def sendSelection(console: ScalaLanguageConsole, text: String) {
    val consoleEditor = console.getConsoleEditor
    val model = ScalaConsoleInfo.getModel(console.getProject)
    val processHandler = ScalaConsoleInfo.getProcessHandler(console.getProject)

    if (consoleEditor != null) {
      val document = console.getEditorDocument
      console.setTextToEditor(text)

      extensions.inWriteAction {
        val range: TextRange = new TextRange(0, document.getTextLength)
        consoleEditor.getSelectionModel.setSelection(range.getStartOffset, range.getEndOffset)
        console.addCurrentToHistory(range, false, true)
        model.addToHistory(text)

        consoleEditor.getCaretModel.moveToOffset(0)
        consoleEditor.getDocument.setText("")
      }

      text.split('\n').foreach(line => {
        if (line != "") {
          val outputStream: OutputStream = processHandler.getProcessInput
          try {
            val bytes: Array[Byte] = (line + "\n").getBytes
            outputStream.write(bytes)
            outputStream.flush()
          }
          catch {
            case e: IOException => //ignore
          }
        }
        console.textSent(line + "\n")
      })
    }
  }
}


