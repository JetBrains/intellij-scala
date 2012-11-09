package org.jetbrains.plugins.scala
package worksheet.actions

import com.intellij.util.containers.WeakHashMap
import com.intellij.openapi.project.Project
import com.intellij.execution.process.ProcessHandler
import com.intellij.openapi.editor.Editor

/**
 * @author Ksenia.Sautina
 * @since 10/15/12
 */
object WorksheetInfo {
  private val allWorksheets =
    new WeakHashMap[Project, List[(ProcessHandler, Editor)]]()

  def getProcessHandler(project: Project): ProcessHandler = {
    synchronized {
      allWorksheets.get(project) match {
        case null => null
        case list => list.headOption.map(_._1).getOrElse(null)
      }
    }
  }

  def getEditor(project: Project): Editor = {
    synchronized {
      allWorksheets.get(project) match {
        case null => null
        case list => list.headOption.map(_._2).getOrElse(null)
      }
    }
  }

  def addWorksheet(project: Project, processHandler: ProcessHandler, editor: Editor) {
    synchronized {
      allWorksheets.get(project) match {
        case null =>
          allWorksheets.put(project, (processHandler, editor) :: Nil)
        case list: List[(ProcessHandler, Editor)] =>
          allWorksheets.put(project, (processHandler, editor) :: list)
      }
    }
  }
}
