package org.jetbrains.plugins.scala.console

import java.io.{IOException, OutputStream}

import com.intellij.execution.console.{LanguageConsoleBuilder, LanguageConsoleImpl, LanguageConsoleView}
import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.{Condition, TextRange}
import com.intellij.util.Consumer
import org.jetbrains.plugins.scala.extensions

/**
  * User: Dmitry.Naydanov
  */
object ScalaLanguageConsoleBuilder {
  class ExecuteScalaConsoleHandler(console: ScalaLanguageConsole) extends Consumer[String] {
    override def consume(text: String) {
      val editor = console.getConsoleEditor
      val processHandler = ScalaConsoleInfo.getProcessHandler(editor)
      val model = ScalaConsoleInfo.getController(editor)
      
      if (editor != null && console != null && processHandler != null && model != null) {
        val document = console.getEditorDocument
        val text = document.getText

        // Process input and add to history
        extensions.inWriteAction {
          val range: TextRange = new TextRange(0, document.getTextLength)
          editor.getSelectionModel.setSelection(range.getStartOffset, range.getEndOffset)
          console.addToHistory(range, console.getEditor.asInstanceOf[EditorEx], true)
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
      }
    }
  }
  
  def createConsole(project: Project): LanguageConsoleImpl = {
    val consoleView = new ScalaLanguageConsole(project, ScalaLanguageConsoleView.SCALA_CONSOLE)

    LanguageConsoleBuilder.registerExecuteAction(consoleView, new ExecuteScalaConsoleHandler(consoleView),
        ScalaLanguageConsoleView.SCALA_CONSOLE, ScalaLanguageConsoleView.SCALA_CONSOLE, new Condition[LanguageConsoleView] {
        override def value(t: LanguageConsoleView): Boolean = true
      })

    ScalaConsoleInfo.setIsConsole(consoleView.getFile, flag = true)
    
    consoleView.setPrompt(null)
    consoleView
  }
}
