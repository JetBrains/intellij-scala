package org.jetbrains.plugins.scala.console

import com.intellij.execution.console.ConsoleHistoryController
import com.intellij.execution.process.ProcessHandler
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiFile
import com.intellij.util.containers.WeakHashMap

/**
 * @author Ksenia.Sautina
 * @since 7/27/12
 */

object ScalaConsoleInfo {
  private val SCALA_LANGUAGE_CONSOLE_KEY = new com.intellij.openapi.util.Key[String]("ScalaLanguageConsoleKey")
  private val NULL = (null, null, null)
  private val allConsoles =
    new WeakHashMap[Project, List[(ScalaLanguageConsole, ConsoleHistoryController, ProcessHandler)]]()

  def getConsole(file: PsiFile): ScalaLanguageConsole = get(file)._1
  def getConsole(project: Project): ScalaLanguageConsole = get(project)._1
  def getController(project: Project): ConsoleHistoryController = get(project)._2
  def getProcessHandler(project: Project): ProcessHandler = get(project)._3
  def getConsole(editor: Editor): ScalaLanguageConsole = get(editor)._1
  def getController(editor: Editor): ConsoleHistoryController = get(editor)._2
  def getProcessHandler(editor: Editor): ProcessHandler = get(editor)._3

  def setIsConsole(file: PsiFile, flag: Boolean) {
    file.putCopyableUserData(SCALA_LANGUAGE_CONSOLE_KEY, if (flag) "console" else null)
  }

  def isConsole(file: PsiFile): Boolean = file.getCopyableUserData(SCALA_LANGUAGE_CONSOLE_KEY) != null
  
  def addConsole(console: ScalaLanguageConsole, model: ConsoleHistoryController, processHandler: ProcessHandler) {
    val project = console.getProject
    synchronized {
      allConsoles.get(project) match {
        case null =>
          allConsoles.put(project, (console, model, processHandler) :: Nil)
        case list: List[(ScalaLanguageConsole, ConsoleHistoryController, ProcessHandler)] =>
          allConsoles.put(project, (console, model, processHandler) :: list)
      }
    }
  }

  def disposeConsole(console: ScalaLanguageConsole) {
    val project = console.getProject
    synchronized {
      allConsoles.get(project) match {
        case null =>
        case list: List[(ScalaLanguageConsole, ConsoleHistoryController, ProcessHandler)] =>
          allConsoles.put(project, list.filter {
            case (sConsole, _, _) => sConsole != console
          })
      }
    }
  }

  private def get(project: Project): (ScalaLanguageConsole, ConsoleHistoryController, ProcessHandler) = {
    synchronized {
      allConsoles.get(project) match {
        case null => NULL
        case list => list.headOption.getOrElse(NULL)
      }
    }
  }

  private def get(editor: Editor) = {
    synchronized {
      allConsoles.get(editor.getProject) match {
        case null => NULL
        case list =>
          list.find {
            case (console: ScalaLanguageConsole, _: ConsoleHistoryController, _: ProcessHandler) =>
              console.getConsoleEditor == editor
          } match {
            case Some(res) => res
            case _ => NULL
          }
      }
    }
  }

  private def get(file: PsiFile) = {
    synchronized {
      allConsoles.get(file.getProject) match {
        case null => NULL
        case list =>
          list.find {
            case (console: ScalaLanguageConsole, _: ConsoleHistoryController, _: ProcessHandler) =>
              console.getFile == file
          } match {
            case Some(res) => res
            case _ => NULL
          }
      }
    }
  }
}
