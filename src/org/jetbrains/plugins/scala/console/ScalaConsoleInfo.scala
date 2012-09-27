package org.jetbrains.plugins.scala.console

import com.intellij.execution.process.{ConsoleHistoryModel, ProcessHandler}
import com.intellij.util.containers.WeakHashMap
import com.intellij.openapi.project.Project

/**
 * @author Ksenia.Sautina
 * @since 7/27/12
 */

object ScalaConsoleInfo {
  private val allConsoles =
    new WeakHashMap[Project, List[(ScalaLanguageConsole, ConsoleHistoryModel, ProcessHandler)]]()

  def getConsole(project: Project): ScalaLanguageConsole = {
    synchronized {
      allConsoles.get(project) match {
        case null => null
        case list => list.headOption.map(_._1).getOrElse(null)
      }
    }
  }

  def getModel(project: Project): ConsoleHistoryModel = {
    synchronized {
      allConsoles.get(project) match {
        case null => null
        case list => list.headOption.map(_._2).getOrElse(null)
      }
    }
  }

  def getProcessHandler(project: Project): ProcessHandler = {
    synchronized {
      allConsoles.get(project) match {
        case null => null
        case list => list.headOption.map(_._3).getOrElse(null)
      }
    }
  }

  def addConsole(console: ScalaLanguageConsole, model: ConsoleHistoryModel, processHandler: ProcessHandler) {
    val project = console.getProject
    synchronized {
      allConsoles.get(project) match {
        case null =>
          allConsoles.put(project, (console, model, processHandler) :: Nil)
        case list: List[(ScalaLanguageConsole, ConsoleHistoryModel, ProcessHandler)] =>
          allConsoles.put(project, (console, model, processHandler) :: list)
      }
    }
  }

  def disposeConsole(console: ScalaLanguageConsole) {
    val project = console.getProject
    synchronized {
      allConsoles.get(project) match {
        case null =>
        case list: List[(ScalaLanguageConsole, ConsoleHistoryModel, ProcessHandler)] =>
          allConsoles.put(project, list.filter {
            case (sConsole, _, _) => sConsole != console
          })
      }
    }
  }
}
