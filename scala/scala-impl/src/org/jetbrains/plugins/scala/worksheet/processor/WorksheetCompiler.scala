package org.jetbrains.plugins.scala
package worksheet.processor

import java.io.File

import com.intellij.compiler.CompilerMessageImpl
import com.intellij.compiler.progress.CompilerTask
import com.intellij.notification.NotificationType
import com.intellij.openapi.compiler.CompilerMessageCategory
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.util.io.FileUtil
import com.intellij.psi.PsiErrorElement
import org.jetbrains.plugins.scala.compiler.CompileServerLauncher
import org.jetbrains.plugins.scala.lang.psi.api.ScalaFile
import org.jetbrains.plugins.scala.util.NotificationUtil
import org.jetbrains.plugins.scala.worksheet.processor.WorksheetCompilerUtil._
import org.jetbrains.plugins.scala.worksheet.runconfiguration.{ReplModeArgs, WorksheetCache}
import org.jetbrains.plugins.scala.worksheet.server._
import org.jetbrains.plugins.scala.worksheet.settings.{WorksheetCommonSettings, WorksheetFileSettings, WorksheetProjectSettings}
import org.jetbrains.plugins.scala.worksheet.ui.{WorksheetEditorPrinter, WorksheetEditorPrinterFactory}

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

  private def createCompilerTask: CompilerTask =
    new CompilerTask(project, s"Worksheet ${worksheetFile.getName} compilation", false, false, false, false)
  
  private def createWorksheetPrinter(isRepl: Boolean): WorksheetEditorPrinter = 
    WorksheetEditorPrinterFactory.newWorksheetUiFor(editor, worksheetFile, isRepl)

  private def runCompilerTask(className: String, code: String, printer: WorksheetEditorPrinter, 
                              tempFile: File, outputDir: File) {
    val afterCompileRunnable = if (runType == OutOfProcessServer) new Runnable {
      override def run(): Unit = callback(className, outputDir.getAbsolutePath)
    } else EMPTY_RUNNABLE

    val task = createCompilerTask
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
  
  private def runDumbTask(printer: WorksheetEditorPrinter, replModeArgs: Option[ReplModeArgs]) {
    val task = createCompilerTask
    
    val consumer = new RemoteServerConnector.CompilerInterfaceImpl(task, printer, None)
    task.start(new Runnable {
      override def run(): Unit = {
        new RemoteServerConnector(worksheetFile, new File(""), new File(""), "", replModeArgs, false).compileAndRun(
          EMPTY_RUNNABLE, worksheetVirtual, consumer)
      }
    }, EMPTY_RUNNABLE)
  }
  
  def compileAndRunCode(request: WorksheetCompileRunRequest, auto: Boolean = false) {
    if (module == null) {
      onError("Can't find Scala module to run")
      return
    }

    WorksheetCompilerUtil.removeOldMessageContent(project) //or not?

    if (runType != NonServer) CompileServerLauncher.ensureServerRunning(project)
    
    @inline def runDumb(code: String, isRepl: Boolean): Unit = 
      runDumbTask(
        createWorksheetPrinter(isRepl),
        Some(ReplModeArgs(worksheetVirtual.getCanonicalPath, code))
      ) 
    
    request match {
      case RunSimple(code) => runDumb(code, isRepl = false)
      case RunRepl(code) => runDumb(code, isRepl = true)
      case RunCompile(code, name) =>
        val (_, tempFile, outputDir) = WorksheetCache.getInstance(project).updateOrCreateCompilationInfo(worksheetVirtual.getCanonicalPath, worksheetFile.getName)
        
        WorksheetCache.getInstance(project).removePrinter(editor)

        runCompilerTask(
          name, code,
          createWorksheetPrinter(false), 
          tempFile, outputDir
        )
      case ErrorWhileCompile(message, position) =>
        if (!auto) 
          WorksheetCompilerUtil.showCompilationError(
            worksheetVirtual, position.line, position.column,
            project, () => {editor.getCaretModel moveToLogicalPosition position}, 
            Array(message)
          )
    }
  }

  def compileAndRunFile() {
    val runRequest = WorksheetSourceProcessor.process(worksheetFile, Option(editor), isRepl) match {
      case Left((code, "")) => RunRepl(code)
      case Left((code, name)) => RunCompile(code, name)
      case Right(errorElement: PsiErrorElement) => 
        ErrorWhileCompile(errorElement.getErrorDescription, editor.offsetToLogicalPosition(errorElement.getTextOffset))
    }
    
    compileAndRunCode(runRequest, auto)
  }
}

object WorksheetCompiler extends WorksheetPerFileConfig {
  private val EMPTY_RUNNABLE = new Runnable {
    override def run(): Unit = {}
  }
  private val CONFIG_ERROR_HEADER = "Worksheet configuration error:"
}
