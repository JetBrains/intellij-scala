package org.jetbrains.plugins.scala.console

import com.intellij.openapi.project.Project
import com.intellij.execution.runners.{AbstractConsoleRunnerWithHistory, ConsoleExecuteActionHandler}
import com.intellij.execution.process.ProcessHandler
import java.lang.String
import com.intellij.execution.console.{ConsoleHistoryController, LanguageConsoleViewImpl}
/**
 * User: Alexander Podkhalyuzin
 * Date: 14.04.2010
 */

class ScalaLanguageConsoleView(project: Project) extends {
  val scalaConsole = new ScalaLanguageConsole(project, ScalaLanguageConsoleView.SCALA_CONSOLE)
} with LanguageConsoleViewImpl(project, scalaConsole) {
  override def attachToProcess(processHandler: ProcessHandler) {
    super.attachToProcess(processHandler)
    val handler = new ConsoleExecuteActionHandler(processHandler, false) {
      override def processLine(line: String) {
        line.split('\n').foreach(line => {
          if (line != "") {
            super.processLine(line)
          }
          scalaConsole.textSent(line + "\n")
        })
      }
    }
    new ConsoleHistoryController("scala", null, scalaConsole, handler.getConsoleHistoryModel).install()
    val action = AbstractConsoleRunnerWithHistory.createConsoleExecAction(scalaConsole, processHandler, handler)
    action.registerCustomShortcutSet(action.getShortcutSet, scalaConsole.getComponent)
  }
}

object ScalaLanguageConsoleView {
  val SCALA_CONSOLE = "Scala Console"
}