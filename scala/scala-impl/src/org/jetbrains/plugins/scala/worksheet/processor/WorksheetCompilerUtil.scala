package org.jetbrains.plugins.scala.worksheet.processor

import com.intellij.compiler.impl.CompilerErrorTreeView
import com.intellij.openapi.editor.{Editor, LogicalPosition}
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiErrorElement
import com.intellij.ui.content.{ContentFactory, MessageView}
import com.intellij.util.ui.MessageCategory
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.project.migration.apiimpl.MigrationApiImpl

/**
  * User: Dmitry.Naydanov
  * Date: 29.05.18.
  */
object WorksheetCompilerUtil {
  private val ERROR_CONTENT_NAME = "Worksheet errors"

  sealed trait WorksheetCompileRunRequest

  case class RunOuter(code: String) extends WorksheetCompileRunRequest
  case class RunSimple(code: String) extends WorksheetCompileRunRequest
  case class RunRepl(code: String) extends WorksheetCompileRunRequest
  case class RunCompile(code: String, className: String) extends WorksheetCompileRunRequest
  case class RunCustom(id: String, project: Project, data: String) extends WorksheetCompileRunRequest
  case class ErrorWhileCompile(message: String, position: LogicalPosition) extends WorksheetCompileRunRequest
  
  object ErrorWhileCompile {
    def apply(message: String, position: LogicalPosition): ErrorWhileCompile = new ErrorWhileCompile(message, position)
    def apply(errorElement: PsiErrorElement, ifEditor: Option[Editor]): ErrorWhileCompile = 
      new ErrorWhileCompile(
        errorElement.getErrorDescription, 
        ifEditor.map(_ offsetToLogicalPosition errorElement.getTextOffset) getOrElse new LogicalPosition(0, 0)
      )
  }
  
  sealed trait CompilationMessageSeverity {
    def toType: Int
    def isFatal: Boolean = false
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
            val errorContent = ContentFactory.SERVICE.getInstance.createContent(newView, ERROR_CONTENT_NAME, true)
            contentManager.addContent(errorContent)
            (errorContent, newView)
        }

      contentManager.setSelectedContent(currentContent)
      MigrationApiImpl.openMessageView(project, currentContent, treeError)

      onShow()
    }
  }

  def showCompilationError(file: VirtualFile, pos: LogicalPosition, msg: Array[String], onShow: () => Unit)
                          (implicit project: Project): Unit =
    showCompilationMessage(file, pos, msg, ErrorSeverity, onShow)

  def removeOldMessageContent(project: Project) {
    val contentManager = MessageView.SERVICE.getInstance(project).getContentManager
    val oldContent = contentManager findContent ERROR_CONTENT_NAME
    if (oldContent != null) {
      contentManager.removeContent(oldContent, true)
    }
  }
}
