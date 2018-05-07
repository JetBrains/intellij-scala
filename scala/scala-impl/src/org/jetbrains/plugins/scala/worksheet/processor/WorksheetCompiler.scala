package org.jetbrains.plugins.scala
package worksheet.processor

import java.io.File

import com.intellij.compiler.CompilerMessageImpl
import com.intellij.compiler.impl.CompilerErrorTreeView
import com.intellij.compiler.progress.CompilerTask
import com.intellij.notification.NotificationType
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.compiler.CompilerMessageCategory
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiErrorElement
import com.intellij.ui.content.{ContentFactory, MessageView}
import com.intellij.util.ui.MessageCategory
import org.jetbrains.plugins.scala.compiler.CompileServerLauncher
import org.jetbrains.plugins.scala.lang.psi.api.ScalaFile
import org.jetbrains.plugins.scala.project.migration.apiimpl.MigrationApiImpl
import org.jetbrains.plugins.scala.util.NotificationUtil
import org.jetbrains.plugins.scala.worksheet.runconfiguration.{ReplModeArgs, WorksheetCache}
import org.jetbrains.plugins.scala.worksheet.server._
import org.jetbrains.plugins.scala.worksheet.settings.{WorksheetCommonSettings, WorksheetFileSettings, WorksheetProjectSettings}
import org.jetbrains.plugins.scala.worksheet.ui.{WorksheetEditorPrinterBase, WorksheetEditorPrinterFactory}

/**
  * User: Dmitry Naydanov
  * Date: 1/15/14
  *
  * @param callback (Name, AddToClasspath)
  */
class WorksheetCompiler(editor: Editor, worksheetFile: ScalaFile, callback: (String, String) => Unit, auto: Boolean) {
  import worksheet.processor.WorksheetCompiler._
  
  private val project = worksheetFile.getProject
  private val runType = WorksheetProjectSettings getRunType project
  private val isRepl = WorksheetFileSettings isRepl worksheetFile
  private val worksheetVirtual = worksheetFile.getVirtualFile
  private val module = WorksheetCommonSettings.getInstance(worksheetFile).getModuleFor

  private def onError(msg: String) {
    NotificationUtil.builder(project, msg).setGroup(
      "Scala").setNotificationType(NotificationType.ERROR).setTitle(CONFIG_ERROR_HEADER).show()
  }

  private def createCompilerTask =
    new CompilerTask(project, s"Worksheet ${worksheetFile.getName} compilation", false, false, false, false)

  private def runCompilerTask(task: CompilerTask, className: String, code: String, printer: WorksheetEditorPrinterBase, 
                              tempFile: File, outputDir: File) {
    val afterCompileRunnable = if (runType == OutOfProcessServer) new Runnable {
      override def run(): Unit = callback(className, outputDir.getAbsolutePath)
    } else EMPTY_RUNNABLE

    val consumer = new RemoteServerConnector.CompilerInterfaceImpl(task, printer, None, auto)

    FileUtil.writeToFile(tempFile, code)

    task.start(new Runnable {
      override def run() {
        try {
          new RemoteServerConnector(
            worksheetFile, tempFile, outputDir, className, None, true
          ).compileAndRun(afterCompileRunnable, worksheetVirtual, consumer)
        }
        catch {
          case ex: IllegalArgumentException => onError(ex.getMessage)
        }
      }
    }, EMPTY_RUNNABLE)
    
    if (WorksheetFileSettings shouldShowReplWarning worksheetFile) task.addMessage(
      new CompilerMessageImpl(project, CompilerMessageCategory.WARNING, "Worksheet can be executed in REPL mode only in compile server process.")
    ) 
  }
  
  private def runDumbTask(task: CompilerTask, printer: WorksheetEditorPrinterBase, 
                          replModeArgs: Option[ReplModeArgs]) {
    val consumer = new RemoteServerConnector.CompilerInterfaceImpl(task, printer, None)
    task.start(new Runnable {
      override def run(): Unit = {
        new RemoteServerConnector(worksheetFile, new File(""), new File(""), "", replModeArgs, false).compileAndRun(
          EMPTY_RUNNABLE, worksheetVirtual, consumer)
      }
    }, EMPTY_RUNNABLE)
  }


  def compileAndRun() {
    if (module == null) {
      onError("Can't find Scala module to run")
      return
    }

    val (iteration, tempFile, outputDir) = 
      if (!isRepl) WorksheetBoundCompilationInfo.updateOrCreate(worksheetVirtual.getCanonicalPath, worksheetFile.getName)
      else (0, null, null)
    if (runType != NonServer) CompileServerLauncher.ensureServerRunning(project)

    WorksheetCompiler.removeOldMessageContent(project)

    WorksheetSourceProcessor.process(worksheetFile, Option(editor), iteration, isRepl) match {
      case Left((code, "")) => 
        val task = createCompilerTask
        val worksheetPrinter = 
          WorksheetEditorPrinterFactory.newWorksheetUiFor(editor, worksheetFile, isRepl = true)
        
        runDumbTask(task, worksheetPrinter, Some(ReplModeArgs(worksheetVirtual.getCanonicalPath, code)))
      case Left((code, name)) =>
        val task = createCompilerTask
        val worksheetPrinter = WorksheetEditorPrinterFactory.newWorksheetUiFor(editor, worksheetFile, isRepl = false)

        WorksheetCache.getInstance(project).removePrinter(editor)
        
        runCompilerTask(task, name, code, worksheetPrinter, tempFile, outputDir)
      case Right(errorMessage: PsiErrorElement) =>
        if (auto) return
        val pos = editor.offsetToLogicalPosition(errorMessage.getTextOffset)

        WorksheetCompiler.showCompilationError(errorMessage.getContainingFile.getVirtualFile, pos.line, pos.column, 
          project, () => {editor.getCaretModel moveToLogicalPosition pos}, Array(errorMessage.getErrorDescription))
      case _ =>
    }
  }
}

object WorksheetCompiler extends WorksheetPerFileConfig {
  private val EMPTY_RUNNABLE = new Runnable {
    override def run(): Unit = {}
  }

  private val ERROR_CONTENT_NAME = "Worksheet errors"
  private val CONFIG_ERROR_HEADER = "Worksheet configuration error:"

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

  def showCompilationMessage(file: VirtualFile, severity: CompilationMessageSeverity, line: Int, column: Int, 
                             project: Project, onShow: () => Unit, msg: Array[String]) {
    val contentManager = MessageView.SERVICE.getInstance(project).getContentManager

    def addMessageToView(treeView: CompilerErrorTreeView): Unit = treeView.addMessage(severity.toType, msg, file, line, column, null)
    
    ApplicationManager.getApplication.invokeLater(new Runnable {
      override def run(): Unit = {
        if (file == null || !file.isValid) return
        
        val (currentContent, treeError) = {
          Option(contentManager findContent ERROR_CONTENT_NAME) match {
            case Some(old) if old.getComponent.isInstanceOf[CompilerErrorTreeView] =>
              val oldView = old.getComponent.asInstanceOf[CompilerErrorTreeView]
              addMessageToView(oldView)
              (old, oldView)
            case None =>
              val newView = new CompilerErrorTreeView(project, null)
              addMessageToView(newView)
              val errorContent = ContentFactory.SERVICE.getInstance.createContent(newView, ERROR_CONTENT_NAME, true)
              contentManager addContent errorContent
              (errorContent, newView)
          }
        }

        contentManager setSelectedContent currentContent
        MigrationApiImpl.openMessageView(project, currentContent, treeError)

        onShow()
      }
    })
  }
  
  def showCompilationError(file: VirtualFile, line: Int, column: Int, 
                           project: Project, onShow: () => Unit, msg: Array[String]): Unit = {
    showCompilationMessage(file, ErrorSeverity, line, column, project, onShow, msg)
  }
  
  private def removeOldMessageContent(project: Project) {
    val contentManager = MessageView.SERVICE.getInstance(project).getContentManager
    val oldContent = contentManager findContent ERROR_CONTENT_NAME
    if (oldContent != null) contentManager.removeContent(oldContent, true)
  }
}
