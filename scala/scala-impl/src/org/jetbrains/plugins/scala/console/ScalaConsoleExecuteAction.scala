package org.jetbrains.plugins.scala
package console

import java.io.{IOException, OutputStream}

import com.intellij.openapi.actionSystem.{AnAction, AnActionEvent, CommonDataKeys}
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.openapi.util.TextRange

/**
 * @author Ksenia.Sautina
 * @since 9/18/12
 */
class ScalaConsoleExecuteAction extends AnAction {
  override def update(e: AnActionEvent) {
    val editor = e.getData(CommonDataKeys.EDITOR)
    if (editor == null || !editor.isInstanceOf[EditorEx]) {
      e.getPresentation.setEnabled(false)
      return
    }
    val console = ScalaConsoleInfo.getConsole(editor)
    if (console == null)  {
      e.getPresentation.setEnabled(false)
      return
    }
    val isEnabled: Boolean = !editor.asInstanceOf[EditorEx].isRendererMode &&
      !ScalaConsoleInfo.getProcessHandler(editor).isProcessTerminated

    e.getPresentation.setEnabled(isEnabled)
  }

  def actionPerformed(e: AnActionEvent) {
    val editor = e.getData(CommonDataKeys.EDITOR)
    if (editor == null) return
    
    val console = ScalaConsoleInfo.getConsole(editor)
    val processHandler = ScalaConsoleInfo.getProcessHandler(editor)
    val model = ScalaConsoleInfo.getController(editor)
    
    if (editor != null && console != null && processHandler != null && model != null) {
      val document = console.getEditorDocument
      val text = document.getText

      // Process input and add to history
      extensions.inWriteAction {
        val range: TextRange = new TextRange(0, document.getTextLength)
        editor.getSelectionModel.setSelection(range.getStartOffset, range.getEndOffset)
        console.addToHistory(range, console.getConsoleEditor, true)
        model.addToHistory(text)

        editor.getCaretModel.moveToOffset(0)
        editor.getDocument.setText("")
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
            case _: IOException => //ignore
          }
        }
        console.textSent(line + "\n")
      })
    } else {
      ScalaConsoleExecuteAction.LOG.info(new Throwable(s"Enter action in console failed: $editor, " +
        s"$console"))
    }
  }
}

object ScalaConsoleExecuteAction {
  private val LOG = Logger.getInstance(this.getClass)
}
