package org.jetbrains.plugins.scala.console

import java.lang.String
import com.intellij.execution.console.{ConsoleHistoryController, LanguageConsoleViewImpl}
import com.intellij.execution.process.{ConsoleHistoryModel, ProcessHandler}
import com.intellij.openapi.project.Project
import java.util.Comparator
import com.intellij.openapi.actionSystem._

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

  override def getData(dataId: String): AnyRef = {
    if (PlatformDataKeys.ACTIONS_SORTER.is(dataId)) {
      ScalaLanguageConsoleView.CONSOLE_ACTIONS_COMPARATOR
    } else super.getData(dataId)
  }

  override def dispose() {
    super.dispose()
    ScalaConsoleInfo.disposeConsole(scalaConsole)
  }
}

object ScalaLanguageConsoleView {
  val SCALA_CONSOLE = "Scala Console"

  private val CONSOLE_ACTIONS_COMPARATOR: Comparator[AnAction] = new Comparator[AnAction] {
    def compare(o1: AnAction, o2: AnAction): Int = {
      (o1, o2) match {
        case (_: ScalaConsoleExecuteAction, _: ScalaConsoleExecuteAction) => 0
        case (_: ScalaConsoleExecuteAction, _) => -1
        case (_, _: ScalaConsoleExecuteAction) => 1
        case _ => 0
      }
    }
  }
}
