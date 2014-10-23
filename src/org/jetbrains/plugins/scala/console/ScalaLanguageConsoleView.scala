package org.jetbrains.plugins.scala.console

import com.intellij.execution.console.{ConsoleHistoryController, LanguageConsoleViewImpl}
import com.intellij.execution.process.{ConsoleHistoryModel, ProcessHandler}
import com.intellij.openapi.project.Project

/**
 * @author Alexander Podkhalyuzin
 * @since 14.04.2010
 */
class ScalaLanguageConsoleView(project: Project) extends {
  val scalaConsole = new ScalaLanguageConsole(project, ScalaLanguageConsoleView.SCALA_CONSOLE)
} with LanguageConsoleViewImpl(scalaConsole) {
  override def attachToProcess(processHandler: ProcessHandler) {
    super.attachToProcess(processHandler)
    val model = new ConsoleHistoryModel
    new ConsoleHistoryController("scala", null, scalaConsole, model).install()

    ScalaConsoleInfo.addConsole(scalaConsole, model, processHandler)
  }

  override def dispose() {
    super.dispose()
    ScalaConsoleInfo.disposeConsole(scalaConsole)
  }
}

object ScalaLanguageConsoleView {
  val SCALA_CONSOLE = "Scala Console"
}
