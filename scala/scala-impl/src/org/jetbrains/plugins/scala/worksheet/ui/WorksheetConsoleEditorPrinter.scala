package org.jetbrains.plugins.scala.worksheet.ui
import com.intellij.execution.ui.{ConsoleView, ConsoleViewContentType}
import com.intellij.openapi.editor.Editor
import com.intellij.ui.content.Content
import org.jetbrains.plugins.scala.lang.psi.api.ScalaFile

/**
  * User: Dmitry.Naydanov
  * Date: 25.05.18.
  */
class WorksheetConsoleEditorPrinter(private val worksheetEditor: Editor, private val file: ScalaFile, 
                                    private val console: ConsoleView, private val relatedContent: Content) extends WorksheetEditorPrinter {
  override def getScalaFile: ScalaFile = file

  override def processLine(line: String): Boolean = {
    if (!isTechnicalMessage(line)) console.print(line, ConsoleViewContentType.NORMAL_OUTPUT)
    true
  }

  override def internalError(errorMessage: String): Unit = {
    console.print(errorMessage, ConsoleViewContentType.ERROR_OUTPUT)
  }
  
  override def flushBuffer(): Unit = {}
  override def scheduleWorksheetUpdate(): Unit = {}
  
  private def isTechnicalMessage(message: String): Boolean = message.startsWith(WorksheetIncrementalEditorPrinter.TECHNICAL_MESSAGE_START)
}
