package org.jetbrains.plugins.scala.worksheet.processor

import com.intellij.compiler.impl.CompilerErrorTreeView
import com.intellij.openapi.editor.LogicalPosition
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.ui.content.{ContentFactory, MessageView}
import com.intellij.util.ui.MessageCategory
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.worksheet.ui.printers.repl.QueuedPsi


private[worksheet]
object WorksheetCompilerUtil {
  private val ERROR_CONTENT_NAME = "Worksheet errors" // TODO: is it effectively used?

  sealed trait WorksheetCompileRunRequest
  object WorksheetCompileRunRequest {
    final case class RunRepl(code: String, evaluatedElements: Seq[QueuedPsi]) extends WorksheetCompileRunRequest
    final case class RunCompile(code: String, className: String) extends WorksheetCompileRunRequest
  }
  
  sealed trait CompilationMessageSeverity {
    def toType: Int
    def isFatal: Boolean = false
  }

  object CompilationMessageSeverity {

    def apply(value: String): Option[CompilationMessageSeverity] = value match {
      case "INFO"    => Some(InfoSeverity)
      case "WARNING" => Some(WarningSeverity)
      case "ERROR"   => Some(ErrorSeverity)
      case _         => None
    }
  }

  object ErrorSeverity extends CompilationMessageSeverity {
    override def toType: Int = MessageCategory.ERROR
    override def isFatal = true
  }

  object WarningSeverity extends CompilationMessageSeverity {
    override def toType: Int = MessageCategory.WARNING
  }

  object InfoSeverity extends CompilationMessageSeverity {
    override def toType: Int = MessageCategory.INFORMATION
  }

  def showCompilationMessage(file: VirtualFile, pos: LogicalPosition,
                             msg: Array[String], severity: CompilationMessageSeverity,
                             onShow: () => Unit)
                            (implicit project: Project): Unit = {
    val contentManager = MessageView.SERVICE.getInstance(project).getContentManager

    def addMessageToView(treeView: CompilerErrorTreeView): Unit =
      treeView.addMessage(severity.toType, msg, file, pos.line, pos.column, null)

    invokeLater {
      if (file == null || !file.isValid) return

      val (currentContent, treeError) =
        Option(contentManager.findContent(ERROR_CONTENT_NAME)) match {
          case Some(old) if old.getComponent.isInstanceOf[CompilerErrorTreeView] =>
            val oldView = old.getComponent.asInstanceOf[CompilerErrorTreeView]
            addMessageToView(oldView)
            (old, oldView)
          case None =>
            val newView = new CompilerErrorTreeView(project, null)
            addMessageToView(newView)
            //noinspection ReferencePassedToNls
            val errorContent = ContentFactory.SERVICE.getInstance.createContent(newView, ERROR_CONTENT_NAME, true)
            contentManager.addContent(errorContent)
            (errorContent, newView)
        }

      contentManager.setSelectedContent(currentContent)

      onShow()
    }
  }

  def showCompilationError(file: VirtualFile, pos: LogicalPosition, msg: Array[String], onShow: () => Unit)
                          (implicit project: Project): Unit =
    showCompilationMessage(file, pos, msg, ErrorSeverity, onShow)

  def removeOldMessageContent(project: Project): Unit = {
    val contentManager = MessageView.SERVICE.getInstance(project).getContentManager
    val oldContent = contentManager findContent ERROR_CONTENT_NAME
    if (oldContent != null) {
      contentManager.removeContent(oldContent, true)
    }
  }
}
