package org.jetbrains.plugins.scala
package worksheet.processor

import java.io.File

import com.intellij.compiler.CompilerMessageImpl
import com.intellij.compiler.progress.CompilerTask
import com.intellij.notification.NotificationType
import com.intellij.openapi.compiler.CompilerMessageCategory
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.util.io.FileUtil
import com.intellij.util.Base64
import org.jetbrains.plugins.scala.compiler.CompileServerLauncher
import org.jetbrains.plugins.scala.lang.psi.api.ScalaFile
import org.jetbrains.plugins.scala.util.NotificationUtil
import org.jetbrains.plugins.scala.worksheet.processor.WorksheetCompilerUtil._
import org.jetbrains.plugins.scala.worksheet.runconfiguration.{ReplModeArgs, WorksheetCache}
import org.jetbrains.plugins.scala.worksheet.server._
import org.jetbrains.plugins.scala.worksheet.settings.{WorksheetCommonSettings, WorksheetFileSettings, WorksheetProjectSettings, WorksheetRunType}
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
  private val makeType = WorksheetProjectSettings.getMakeType(project)
  private val runType = WorksheetFileSettings.getRunType(worksheetFile)
  private val worksheetVirtual = worksheetFile.getVirtualFile
  private val module = WorksheetCommonSettings.getInstance(worksheetFile).getModuleFor

  private def onError(msg: String) {
    NotificationUtil.builder(project, msg).setGroup(
      "Scala").setNotificationType(NotificationType.ERROR).setTitle(CONFIG_ERROR_HEADER).show()
  }

  private def createCompilerTask: CompilerTask =
    new CompilerTask(project, s"Worksheet ${worksheetFile.getName} compilation", false, false, false, false)
  
  private def createWorksheetPrinter(runType: WorksheetRunType): WorksheetEditorPrinter = 
    WorksheetEditorPrinterFactory.newWorksheetUiFor(editor, worksheetFile, runType)

  private def runCompilerTask(className: String, code: String, printer: WorksheetEditorPrinter, 
                              tempFile: File, outputDir: File) {
    val afterCompileRunnable = if (makeType == OutOfProcessServer) new Runnable {
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
  
  def compileAndRunCode(request: WorksheetCompileRunRequest) {
    if (module == null) {
      onError("Can't find Scala module to run")
      return
    }

    WorksheetCompilerUtil.removeOldMessageContent(project) //or not?

    if (makeType != NonServer) CompileServerLauncher.ensureServerRunning(project)
    
    @inline def runDumb(code: String, runType: WorksheetRunType): Unit = 
      runDumbTask(
        createWorksheetPrinter(runType),
        Some(ReplModeArgs(worksheetVirtual.getCanonicalPath, code))
      ) 
    
    request match {
      case RunOuter(code) => runDumb(Base64.encode(code.getBytes), WorksheetRunType.REPL_CELL) //assuming that can be called from external code
      case RunSimple(code) => runDumb(code, WorksheetRunType.REPL_CELL)
      case RunRepl(code) => runDumb(code, WorksheetRunType.REPL)
      case RunCompile(code, name) =>
        val (_, tempFile, outputDir) = WorksheetCache.getInstance(project).updateOrCreateCompilationInfo(worksheetVirtual.getCanonicalPath, worksheetFile.getName)
        
        WorksheetCache.getInstance(project).removePrinter(editor)

        runCompilerTask(
          name, code,
          createWorksheetPrinter(WorksheetRunType.PLAIN), 
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
    compileAndRunCode(WorksheetSourceProcessor.process(worksheetFile, Option(editor), runType))
  }
}

object WorksheetCompiler extends WorksheetPerFileConfig {
  private val EMPTY_RUNNABLE = new Runnable {
    override def run(): Unit = {}
  }
  private val CONFIG_ERROR_HEADER = "Worksheet configuration error:"
}
