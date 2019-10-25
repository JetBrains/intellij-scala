package org.jetbrains.plugins.scala
package worksheet.processor

import java.io.File

import com.intellij.compiler.CompilerMessageImpl
import com.intellij.compiler.progress.CompilerTask
import com.intellij.execution.CantRunException
import com.intellij.execution.configurations.JavaParameters
import com.intellij.execution.process.{OSProcessHandler, ProcessAdapter, ProcessEvent}
import com.intellij.execution.ui.ConsoleViewContentType
import com.intellij.ide.util.EditorHelper
import com.intellij.notification.NotificationType
import com.intellij.openapi.compiler.CompilerMessageCategory
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.{JavaSdkType, JdkUtil}
import com.intellij.openapi.roots.ModuleRootManager
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.io.FileUtil
import com.intellij.psi.PsiFile
import org.jetbrains.plugins.scala.compiler.CompileServerLauncher
import org.jetbrains.plugins.scala.extensions.invokeLater
import org.jetbrains.plugins.scala.lang.psi.api.ScalaFile
import org.jetbrains.plugins.scala.project._
import org.jetbrains.plugins.scala.util.NotificationUtil
import org.jetbrains.plugins.scala.worksheet.processor.WorksheetCompiler.EvaluationCallback
import org.jetbrains.plugins.scala.worksheet.processor.WorksheetCompilerUtil._
import org.jetbrains.plugins.scala.worksheet.runconfiguration.{ReplModeArgs, WorksheetCache}
import org.jetbrains.plugins.scala.worksheet.server.RemoteServerConnector.RemoteServerConnectorResult
import org.jetbrains.plugins.scala.worksheet.server._
import org.jetbrains.plugins.scala.worksheet.settings.WorksheetExternalRunType.{PlainRunType, WorksheetPreprocessError}
import org.jetbrains.plugins.scala.worksheet.settings._
import org.jetbrains.plugins.scala.worksheet.ui.printers.WorksheetEditorPrinter

class WorksheetCompiler(
  module: Module,
  editor: Editor,
  worksheetFile: ScalaFile,
  callback: EvaluationCallback,
  auto: Boolean
) {
  import worksheet.processor.WorksheetCompiler._

  private implicit val project: Project = worksheetFile.getProject

  private val makeType = WorksheetProjectSettings.getMakeType(project)
  private val runType = WorksheetFileSettings.getRunType(worksheetFile)
  private val worksheetVirtual = worksheetFile.getVirtualFile

  private def showErrorNotification(msg: String): Unit =
    NotificationUtil.builder(project, msg)
      .setGroup("Scala")
      .setNotificationType(NotificationType.ERROR)
      .setTitle(ConfigErrorHeader)
      .show()

  private def createCompilerTask: CompilerTask =
    new CompilerTask(project, s"Worksheet ${worksheetFile.getName} compilation", false, false, false, false)

  private def createWorksheetPrinter: Option[WorksheetEditorPrinter] =
    Option(runType.createPrinter(editor, worksheetFile))

  private def runCompilerTask(className: String, code: String, tempFile: File, outputDir: File): Unit = {
    val afterCompileCallback: RemoteServerConnectorResult => Unit = {
      case RemoteServerConnectorResult.Compiled =>
        executeWorksheet(worksheetFile.getName, worksheetFile, className, outputDir.getAbsolutePath)(callback)(module)
      case RemoteServerConnectorResult.CompiledAndEvaluated =>
        callback(WorksheetCompilerResult.Done)
      case error: RemoteServerConnectorResult.Error =>
        callback(WorksheetCompilerResult.RemoteServerConnectorError(error))
    }

    val task = createCompilerTask
    val consumer = new RemoteServerConnector.CompilerInterfaceImpl(task, createWorksheetPrinter, None, auto)

    FileUtil.writeToFile(tempFile, code)

    val compileWork: Runnable = () => try {
      val connector = new RemoteServerConnector(module, worksheetFile, tempFile, outputDir, className, None, true)
      connector.compileAndRun(worksheetVirtual, consumer, afterCompileCallback)
    } catch {
      case ex: IllegalArgumentException =>
        showErrorNotification(ex.getMessage)
    }
    task.start(compileWork, EmptyRunnable)

    if (WorksheetFileSettings.shouldShowReplWarning(worksheetFile)) {
      val message = "Worksheet can be executed in REPL mode only in compile server process."
      task.addMessage(new CompilerMessageImpl(project, CompilerMessageCategory.WARNING, message))
    }
  }

  private def runDumbTask(replModeArgs: ReplModeArgs): Unit = {
    val afterCompileCallback: RemoteServerConnectorResult => Unit = {
      case RemoteServerConnectorResult.Compiled =>
        val error = new AssertionError("Worksheet is expected to be evaluated in REPL mode")
        callback(WorksheetCompilerResult.EvaluationError(error))
      case RemoteServerConnectorResult.CompiledAndEvaluated =>
        callback(WorksheetCompilerResult.Done)
      case error: RemoteServerConnectorResult.Error =>
        callback(WorksheetCompilerResult.RemoteServerConnectorError(error))
    }
    val task = createCompilerTask

    val consumer = new RemoteServerConnector.CompilerInterfaceImpl(task, createWorksheetPrinter, None)
    val compileWork: Runnable = () => try {
      val connector = new RemoteServerConnector(module, worksheetFile, new File(""), new File(""), "", Some(replModeArgs), false)
      connector.compileAndRun( worksheetVirtual, consumer, afterCompileCallback)
    } catch {
      case ex: IllegalArgumentException =>
        showErrorNotification(ex.getMessage)
    }
    task.start(compileWork, EmptyRunnable)
  }

  private def compileAndRunCode(request: WorksheetCompileRunRequest): Unit = {
    WorksheetCompilerUtil.removeOldMessageContent(project) //or not?

    makeType match {
      case InProcessServer | OutOfProcessServer =>
        CompileServerLauncher.ensureServerRunning(project)
      case NonServer =>
    }

    request match {
      case RunRepl(code) =>
        val args = ReplModeArgs(worksheetVirtual.getCanonicalPath, code)
        runDumbTask(args)

      case RunCompile(code, name) =>
        val cache = WorksheetCache.getInstance(project)
        val (_, tempFile, outputDir) = cache.updateOrCreateCompilationInfo(worksheetVirtual.getCanonicalPath, worksheetFile.getName)
        cache.removePrinter(editor)
        runCompilerTask(name, code, tempFile, outputDir)
    }
  }

  def compileAndRunFile(): Unit =
    runType.process(worksheetFile, editor) match {
      case Right(request) =>
        compileAndRunCode(request)

      case Left(preprocessError) =>
        showCompilationError(preprocessError)
        callback(WorksheetCompilerResult.PreprocessError(preprocessError))
    }

  private def showCompilationError(error: WorksheetPreprocessError): Unit = {
    WorksheetCompilerUtil.showCompilationError(
      worksheetVirtual, error.position, Array(error.message),
      () => editor.getCaretModel.moveToLogicalPosition(error.position)
    )
  }
}

object WorksheetCompiler extends WorksheetPerFileConfig {

  private val EmptyRunnable: Runnable = () => {}
  private val ConfigErrorHeader = "Worksheet configuration error:"

  type EvaluationCallback = WorksheetCompilerResult => Unit

  sealed trait WorksheetCompilerResult
  object WorksheetCompilerResult {
    object Done extends WorksheetCompilerResult

    sealed trait WorksheetEvaluationError extends WorksheetCompilerResult
    case class PreprocessError(error: WorksheetPreprocessError) extends WorksheetEvaluationError
    case class EvaluationError(reason: Throwable) extends WorksheetEvaluationError
    case class ProcessTerminatedError(returnCode: Int, message: String) extends WorksheetEvaluationError
    case class RemoteServerConnectorError(error: RemoteServerConnectorResult.Error) extends WorksheetEvaluationError
  }

  private val RunnerClassName = "org.jetbrains.plugins.scala.worksheet.MyWorksheetRunner"

  /**
   * FYI: REPL mode works only if we use compiler server and run worksheet with "InProcess" setting
   * this method is only used when run type is [[org.jetbrains.plugins.scala.worksheet.server.OutOfProcessServer]]
   */
  private def executeWorksheet(name: String, scalaFile: ScalaFile, mainClassName: String, addToCp: String)
                              (callback: EvaluationCallback)
                              (implicit module: Module): Unit =
    invokeLater {
      try {
        val params: JavaParameters = createDefaultParameters(scalaFile, mainClassName, addToCp)
        val processHandler = params.createOSProcessHandler()
        setUpUiAndRun(processHandler, scalaFile)(callback)
      } catch {
        case ex: Throwable =>
          callback(WorksheetCompilerResult.EvaluationError(ex))
          throw ex
      }
    }

  private def createDefaultParameters(file: PsiFile, mainClassName: String, addToCp: String)
                                     (implicit module: Module): JavaParameters =
    createParameters(
      module = module,
      mainClassName = mainClassName,
      workingDirectory = Option(module.getProject.baseDir).fold("")(_.getPath),
      additionalCp = addToCp,
      consoleArgs = "",
      worksheetField = file.getVirtualFile.getCanonicalPath
    )

  private def createParameters(module: Module,
                               mainClassName: String,
                               workingDirectory: String,
                               additionalCp: String,
                               consoleArgs: String,
                               worksheetField: String): JavaParameters = {
    val project = module.getProject

    val rootManager = ModuleRootManager.getInstance(module)
    val sdk = rootManager.getSdk
    if (sdk == null || !sdk.getSdkType.isInstanceOf[JavaSdkType]) {
      throw CantRunException.noJdkForModule(module)
    }

    val params = new JavaParameters()

    params.getClassPath.addScalaClassPath(module)
    params.setUseDynamicClasspath(JdkUtil.useDynamicClasspath(project))
    params.getClassPath.addRunners()
    params.setWorkingDirectory(workingDirectory)
    params.setMainClass(RunnerClassName)
    params.configureByModule(module, JavaParameters.JDK_AND_CLASSES_AND_TESTS)

    params.getClassPath.addRunners()
    params.getClassPath.add(additionalCp)
    params.getProgramParametersList.addParametersString(worksheetField)
    if (!consoleArgs.isEmpty)
      params.getProgramParametersList.addParametersString(consoleArgs)
    params.getProgramParametersList.prepend(mainClassName) //IMPORTANT! this must be first program argument

    params
  }

  private def setUpUiAndRun(handler: OSProcessHandler, scalaFile: ScalaFile)
                           (callback: EvaluationCallback): Unit = {
    val editor = EditorHelper.openInEditor(scalaFile)

    val worksheetPrinter = PlainRunType.createPrinter(editor, scalaFile)

    val myProcessListener: ProcessAdapter = new ProcessAdapter {
      override def onTextAvailable(event: ProcessEvent, outputType: Key[_]) {
        val isStdOutput = ConsoleViewContentType.getConsoleViewType(outputType) == ConsoleViewContentType.NORMAL_OUTPUT
        if (isStdOutput) {
          val text = event.getText
          worksheetPrinter.processLine(text)
        }
      }

      override def processTerminated(event: ProcessEvent): Unit = {
        worksheetPrinter.flushBuffer()
        val result = event.getExitCode match {
          case 0  => WorksheetCompilerResult.Done
          case rc => WorksheetCompilerResult.ProcessTerminatedError(rc, event.getText)
        }
        callback.apply(result)
      }
    }

    handler.addProcessListener(myProcessListener)
    handler.startNotify()
  }
}
