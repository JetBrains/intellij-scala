package org.jetbrains.plugins.scala.console

import com.intellij.execution.console.ConsoleHistoryController
import com.intellij.execution.process.ProcessHandler
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiFile

import java.util

object ScalaConsoleInfo {
  private val SCALA_LANGUAGE_CONSOLE_KEY = new com.intellij.openapi.util.Key[String]("ScalaLanguageConsoleKey")
  private val CONSOLE = "console"
  private val NULL = (null, null, null)
  private val allConsoles =
    new util.WeakHashMap[Project, List[(ScalaLanguageConsole, ConsoleHistoryController, ProcessHandler)]]()

  def getConsole(file: PsiFile): ScalaLanguageConsole = get(file)._1
  def getConsole(project: Project): ScalaLanguageConsole = get(project)._1
  def getConsole(editor: Editor): ScalaLanguageConsole = get(editor)._1

  def getController(project: Project): ConsoleHistoryController = get(project)._2
  def getController(editor: Editor): ConsoleHistoryController = get(editor)._2

  def getProcessHandler(project: Project): ProcessHandler = get(project)._3
  def getProcessHandler(editor: Editor): ProcessHandler = get(editor)._3

  def setIsConsole(file: PsiFile, flag: Boolean): Unit = file.putCopyableUserData(SCALA_LANGUAGE_CONSOLE_KEY, if (flag) CONSOLE else null)
  def isConsole(file: PsiFile): Boolean = file.getCopyableUserData(SCALA_LANGUAGE_CONSOLE_KEY) != null

  def setIsConsole(editor: Editor, flag: Boolean): Unit = editor.putUserData(SCALA_LANGUAGE_CONSOLE_KEY, if (flag) CONSOLE else null)
  def isConsole(editor: Editor): Boolean = editor.getUserData(SCALA_LANGUAGE_CONSOLE_KEY) != null

  def addConsole(console: ScalaLanguageConsole, model: ConsoleHistoryController, processHandler: ProcessHandler): Unit = {
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

  private def get(project: Project): (ScalaLanguageConsole, ConsoleHistoryController, ProcessHandler) = {
    synchronized {
      allConsoles.get(project) match {
        case null => NULL
        case list => list.headOption.getOrElse(NULL)
      }
    }
  }

  private def get(editor: Editor): (ScalaLanguageConsole, ConsoleHistoryController, ProcessHandler) = {
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

  private def get(file: PsiFile): (ScalaLanguageConsole, ConsoleHistoryController, ProcessHandler) = {
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
