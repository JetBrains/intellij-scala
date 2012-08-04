package org.jetbrains.plugins.scala.console

import java.lang.String
import com.intellij.execution.console.{ConsoleHistoryController, LanguageConsoleViewImpl}
import com.intellij.execution.process.{ConsoleHistoryModel, ProcessHandler}
import java.io.{IOException, OutputStream}
import com.intellij.openapi.project.{DumbAwareAction, Project}
import java.util.Comparator
import com.intellij.openapi.editor.actions.EnterAction
import com.intellij.openapi.actionSystem._
import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.openapi.util.{IconLoader, TextRange}
import org.jetbrains.plugins.scala.extensions

/**
 * @author Alexander Podkhalyuzin
 * @since 14.04.2010
 */
class ScalaLanguageConsoleView(project: Project) extends {
  val scalaConsole = new ScalaLanguageConsole(project, ScalaLanguageConsoleView.SCALA_CONSOLE)
} with LanguageConsoleViewImpl(project, scalaConsole) {
  override def attachToProcess(processHandler: ProcessHandler) {
    super.attachToProcess(processHandler)
    val model = new ConsoleHistoryModel
    new ConsoleHistoryController("scala", null, scalaConsole, model).install()
    val action = new ScalaExecuteConsoleEnterAction(scalaConsole, processHandler, model)
    EmptyAction.setupAction(action, "Console.Execute", this)
    action.registerCustomShortcutSet(action.getShortcutSet, this)

    ScalaConsoleInfo.addConsole(scalaConsole)
    ScalaConsoleInfo.addModel(model)
    ScalaConsoleInfo.addProcessHandler(processHandler)
  }

  override def getData(dataId: String): AnyRef = {
    if (PlatformDataKeys.ACTIONS_SORTER.is(dataId)) {
      ScalaLanguageConsoleView.CONSOLE_ACTIONS_COMPARATOR
    } else super.getData(dataId)
  }

  override def dispose() {
    super.dispose()
    ScalaConsoleInfo.dispose()
  }
}

class ScalaExecuteConsoleEnterAction(console: ScalaLanguageConsole, processHandler: ProcessHandler,
                                     model: ConsoleHistoryModel) extends DumbAwareAction(null, null, IconLoader.getIcon("/actions/execute.png")) {
  def actionPerformed(e: AnActionEvent) {
    val editor = PlatformDataKeys.EDITOR.getData(e.getDataContext)
    if (editor != null) {
      val document = console.getEditorDocument
      val text = document.getText

      // Process input and add to history
      extensions.inWriteAction {
        val range: TextRange = new TextRange(0, document.getTextLength)
        editor.getSelectionModel.setSelection(range.getStartOffset, range.getEndOffset)
        console.addCurrentToHistory(range, false, true)
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
            case e: IOException => //ignore
          }
        }
        console.textSent(line + "\n")
      })
    }
  }

  override def update(e: AnActionEvent) {
    val editor: EditorEx = console.getConsoleEditor
    e.getPresentation.setEnabled(!editor.isRendererMode && !processHandler.isProcessTerminated)
  }
}

object ScalaLanguageConsoleView {
  val SCALA_CONSOLE = "Scala Console"

  private val CONSOLE_ACTIONS_COMPARATOR: Comparator[AnAction] = new Comparator[AnAction] {
    def compare(o1: AnAction, o2: AnAction): Int = {
      if (o1.isInstanceOf[ScalaExecuteConsoleEnterAction] && o2.isInstanceOf[EnterAction]) -1
      else if (o1.isInstanceOf[ScalaExecuteConsoleEnterAction] || o2.isInstanceOf[ScalaExecuteConsoleEnterAction]) 1
      else 0
    }
  }
}
