package org.jetbrains.plugins.scala.worksheet.ui
import com.intellij.execution.ui.{ConsoleView, ConsoleViewContentType}
import com.intellij.openapi.editor.Editor
import com.intellij.ui.content.Content
import org.jetbrains.plugins.scala.lang.psi.api.ScalaFile

/**
  * User: Dmitry.Naydanov
  * Date: 25.05.18.
  */
class WorksheetConsoleEditorPrinter(private val worksheetEditor: Editor, private val file: ScalaFile) extends WorksheetEditorPrinter {
  private val (_, console) = WorksheetToolWindowFactory.createOutputContent(file) match {
    case Some((content, consoleView)) => (Option(content), Option(consoleView))
    case _ => (None, None)
  }
  
  override def getScalaFile: ScalaFile = file

  override def processLine(line: String): Boolean = {
    if (!isTechnicalMessage(line)) console.foreach(_.print(line, ConsoleViewContentType.NORMAL_OUTPUT))
    true
  }

  override def internalError(errorMessage: String): Unit = {
    console.foreach(_.print(errorMessage, ConsoleViewContentType.ERROR_OUTPUT))
  }
  
  override def flushBuffer(): Unit = {}
  override def scheduleWorksheetUpdate(): Unit = {}
  
  private def isTechnicalMessage(message: String): Boolean = message.startsWith(WorksheetIncrementalEditorPrinter.TECHNICAL_MESSAGE_START)
}
