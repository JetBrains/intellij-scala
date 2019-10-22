package org.jetbrains.plugins.scala
package worksheet.processor

import java.io.File

import com.intellij.compiler.CompilerMessageImpl
import com.intellij.compiler.progress.CompilerTask
import com.intellij.notification.NotificationType
import com.intellij.openapi.compiler.CompilerMessageCategory
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.io.FileUtil
import com.intellij.util.Base64
import org.jetbrains.plugins.scala.compiler.CompileServerLauncher
import org.jetbrains.plugins.scala.lang.psi.api.ScalaFile
import org.jetbrains.plugins.scala.util.NotificationUtil
import org.jetbrains.plugins.scala.worksheet.processor.WorksheetCompilerUtil._
import org.jetbrains.plugins.scala.worksheet.runconfiguration.{ReplModeArgs, WorksheetCache}
import org.jetbrains.plugins.scala.worksheet.server._
import org.jetbrains.plugins.scala.worksheet.settings._
import org.jetbrains.plugins.scala.worksheet.ui.printers.WorksheetEditorPrinter

/**
  * User: Dmitry Naydanov
  * Date: 1/15/14
  *
  * @param callback (Name, AddToClasspath)
  */
class WorksheetCompiler(editor: Editor, worksheetFile: ScalaFile, callback: (String, String) => Unit, auto: Boolean) {
  import worksheet.processor.WorksheetCompiler._
  
  private implicit val project: Project = worksheetFile.getProject

  private val makeType = WorksheetProjectSettings.getMakeType(project)
  private val runType = WorksheetFileSettings.getRunType(worksheetFile)
  private val worksheetVirtual = worksheetFile.getVirtualFile
  private val module = WorksheetCommonSettings(worksheetFile).getModuleFor

  private def onError(msg: String): Unit = {
    NotificationUtil.builder(project, msg)
      .setGroup("Scala")
      .setNotificationType(NotificationType.ERROR).setTitle(CONFIG_ERROR_HEADER).show()
  }

  private def createCompilerTask: CompilerTask =
    new CompilerTask(project, s"Worksheet ${worksheetFile.getName} compilation", false, false, false, false)
  
  private def createWorksheetPrinter: Option[WorksheetEditorPrinter] =
    Option(runType.createPrinter(editor, worksheetFile))

  private def runCompilerTask(className: String, code: String, tempFile: File, outputDir: File): Unit = {
    val afterCompileRunnable: Runnable =
      if (makeType == OutOfProcessServer) () => callback(className, outputDir.getAbsolutePath)
      else EMPTY_RUNNABLE

    val task = createCompilerTask
    val consumer = new RemoteServerConnector.CompilerInterfaceImpl(task, createWorksheetPrinter, None, auto)

    FileUtil.writeToFile(tempFile, code)

    val compileWork: Runnable = () => try {
      val connector = new RemoteServerConnector(worksheetFile, tempFile, outputDir, className, None, true)
      connector.compileAndRun(afterCompileRunnable, worksheetVirtual, consumer)
    } catch {
      case ex: IllegalArgumentException => onError(ex.getMessage)
    }
    task.start(compileWork, EMPTY_RUNNABLE)

    if (WorksheetFileSettings shouldShowReplWarning worksheetFile) {
      val message = "Worksheet can be executed in REPL mode only in compile server process."
      task.addMessage(new CompilerMessageImpl(project, CompilerMessageCategory.WARNING, message))
    }
  }
  
  private def runDumbTask(replModeArgs: Option[ReplModeArgs]): Unit = {
    val task = createCompilerTask
    
    val consumer = new RemoteServerConnector.CompilerInterfaceImpl(task, createWorksheetPrinter, None)
    val compileWork: Runnable = () => {
      val connector = new RemoteServerConnector(worksheetFile, new File(""), new File(""), "", replModeArgs, false)
      connector.compileAndRun(EMPTY_RUNNABLE, worksheetVirtual, consumer)
    }
    task.start(compileWork, EMPTY_RUNNABLE)
  }
  
  def compileAndRunCode(request: WorksheetCompileRunRequest): Unit = {
    if (module == null) {
      onError("Can't find Scala module to run")
      return
    }

    WorksheetCompilerUtil.removeOldMessageContent(project) //or not?

    if (makeType != NonServer) {
      CompileServerLauncher.ensureServerRunning(project)
    }
    
    @inline def runDumb(code: String): Unit = 
      runDumbTask(Some(ReplModeArgs(worksheetVirtual.getCanonicalPath, code)))

    request match {
      case request: RunCustom     => WorksheetCustomRunner.findSuitableRunnerFor(request).foreach(_ handle request)
      case RunOuter(code)         => runDumb(Base64.encode(code.getBytes)) //assuming that can be called from external code
      case RunSimple(code)        => runDumb(code)
      case RunRepl(code)          => runDumb(code)
      case RunCompile(code, name) =>
        val (_, tempFile, outputDir) = WorksheetCache.getInstance(project).updateOrCreateCompilationInfo(worksheetVirtual.getCanonicalPath, worksheetFile.getName)
        WorksheetCache.getInstance(project).removePrinter(editor)
        runCompilerTask(name, code, tempFile, outputDir)
      case ErrorWhileCompile(message, position) if !auto =>
         WorksheetCompilerUtil.showCompilationError(
           worksheetVirtual, position, Array(message),
           () => editor.getCaretModel.moveToLogicalPosition(position)
         )
      case _ =>
    }
  }

  def compileAndRunFile(): Unit = {
    compileAndRunCode(runType.process(worksheetFile, editor))
  }
}

object WorksheetCompiler extends WorksheetPerFileConfig {
  private val EMPTY_RUNNABLE: Runnable = () => {}
  private val CONFIG_ERROR_HEADER = "Worksheet configuration error:"
}
