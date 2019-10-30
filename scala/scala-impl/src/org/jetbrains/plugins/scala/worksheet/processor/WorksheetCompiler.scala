package org.jetbrains.plugins.scala
package worksheet.processor

import java.io.File

import com.intellij.compiler.progress.CompilerTask
import com.intellij.execution.CantRunException
import com.intellij.execution.configurations.JavaParameters
import com.intellij.execution.process.{OSProcessHandler, ProcessAdapter, ProcessEvent}
import com.intellij.execution.ui.ConsoleViewContentType
import com.intellij.notification.NotificationType
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.compiler.{CompilerMessage, CompilerMessageCategory}
import com.intellij.openapi.editor.{Editor, LogicalPosition}
import com.intellij.openapi.module.Module
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.{JavaSdkType, JdkUtil}
import com.intellij.openapi.roots.ModuleRootManager
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.io.FileUtil
import com.intellij.psi.PsiFile
import org.jetbrains.plugins.scala.compiler.CompileServerLauncher
import org.jetbrains.plugins.scala.extensions.{ThrowableExt, invokeLater}
import org.jetbrains.plugins.scala.lang.psi.api.ScalaFile
import org.jetbrains.plugins.scala.project._
import org.jetbrains.plugins.scala.util.NotificationUtil
import org.jetbrains.plugins.scala.worksheet.processor.WorksheetCompiler.EvaluationCallback
import org.jetbrains.plugins.scala.worksheet.processor.WorksheetCompiler.WorksheetCompilerResult.PreconditionError
import org.jetbrains.plugins.scala.worksheet.processor.WorksheetCompilerUtil._
import org.jetbrains.plugins.scala.worksheet.runconfiguration.{ReplModeArgs, WorksheetCache}
import org.jetbrains.plugins.scala.worksheet.server.RemoteServerConnector.{CompilerInterface, CompilerMessagesConsumer, RemoteServerConnectorResult}
import org.jetbrains.plugins.scala.worksheet.server._
import org.jetbrains.plugins.scala.worksheet.settings.WorksheetExternalRunType.WorksheetPreprocessError
import org.jetbrains.plugins.scala.worksheet.settings._
import org.jetbrains.plugins.scala.worksheet.ui.printers.{WorksheetEditorPrinter, WorksheetEditorPrinterRepl}

import scala.collection.mutable
import scala.util.control.NonFatal

private[worksheet]
class WorksheetCompiler(
  module: Module,
  editor: Editor,
  worksheetFile: ScalaFile,
  originalCallback: EvaluationCallback,
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

  private def createWorksheetPrinter: WorksheetEditorPrinter =
    runType.createPrinter(editor, worksheetFile)

  private def runPlainCompilerTask(className: String, code: String, tempFile: File, outputDir: File)
                                  (task: CompilerTask,
                                   afterCompileCallback: RemoteServerConnectorResult => Unit,
                                   consumer: RemoteServerConnector.CompilerInterface): Unit = {
    FileUtil.writeToFile(tempFile, code)

    val compileWork: Runnable = () => try {
      val connector = new RemoteServerConnector(module, worksheetFile, tempFile, outputDir, className, None, true)
      connector.compileAndRun(worksheetVirtual, consumer, afterCompileCallback)
    } catch {
      case ex: IllegalArgumentException =>
        showErrorNotification(ex.getMessage)
    }
    task.start(compileWork, EmptyRunnable)
  }

  private def runReplCompilerTask(replModeArgs: ReplModeArgs)
                                 (task: CompilerTask,
                                  afterCompileCallback: RemoteServerConnectorResult => Unit,
                                  consumer: RemoteServerConnector.CompilerInterface): Unit = {
    val compileWork: Runnable = () => try {
      val connector = new RemoteServerConnector(module, worksheetFile, new File(""), new File(""), "", Some(replModeArgs), false)
      connector.compileAndRun(worksheetVirtual, consumer, afterCompileCallback)
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

    val task = createCompilerTask
    val printer = createWorksheetPrinter
    val consumer = new CompilerInterfaceImpl(task, printer)
    printer match {
      case replPrinter: WorksheetEditorPrinterRepl =>
        replPrinter.updateMessagesConsumer(consumer)
      case _ =>
    }

    // TODO: this is needed to close the timer of printer in one place
    //  the solution is quite ugly when we use raw callbacks, consider using some lightweight streaming/reactive library
    val callback: EvaluationCallback = result => {
      printer.close()
      originalCallback(result)
    }

    val afterCompileCallback: RemoteServerConnectorResult => Unit = {
      case RemoteServerConnectorResult.Compiled(className, outputDir) =>
        if (runType.isReplRunType){
          val error = new AssertionError("Worksheet is expected to be evaluated in REPL mode")
          callback(WorksheetCompilerResult.EvaluationError(error))
        } else {
          executeWorksheet(worksheetFile.getName, worksheetFile, className, outputDir.getAbsolutePath)(callback, printer)(module)
        }
      case RemoteServerConnectorResult.CompiledAndEvaluated =>
        callback(WorksheetCompilerResult.CompiledAndEvaluated)
      case RemoteServerConnectorResult.CompilationError =>
        callback(WorksheetCompilerResult.CompilationError)
      case error: RemoteServerConnectorResult.Error =>
        callback(WorksheetCompilerResult.RemoteServerConnectorError(error))
    }

    val cache = WorksheetCache.getInstance(project)
    if (ApplicationManager.getApplication.isUnitTestMode) {
      cache.addCompilerMessagesCollector(editor, consumer)
    }

    request match {
      case RunRepl(code) =>
        val args = ReplModeArgs(worksheetVirtual.getCanonicalPath, code)
        runReplCompilerTask(args)(task, afterCompileCallback, consumer)

      case RunCompile(code, name) =>
        val (_, tempFile, outputDir) = cache.updateOrCreateCompilationInfo(worksheetVirtual.getCanonicalPath, worksheetFile.getName)
        runPlainCompilerTask(name, code, tempFile, outputDir)(task, afterCompileCallback, consumer)
    }
  }

  def compileAndRunFile(): Unit = {
    if (runType.isReplRunType && makeType != InProcessServer) {
      val error = PreconditionError("Worksheet can be executed in REPL mode only in compile server process")
      showCompilationError(error.message, new LogicalPosition(0, 0))
      originalCallback(error)
    } else {
      runType.process(worksheetFile, editor) match {
        case Right(request) =>
          compileAndRunCode(request)

        case Left(error@WorksheetPreprocessError(message, position)) =>
          showCompilationError(message, position)
          originalCallback(WorksheetCompilerResult.PreprocessError(error))
      }
    }
  }

  private def showCompilationError(message: String, position: LogicalPosition): Unit = {
    WorksheetCompilerUtil.showCompilationError(
      worksheetVirtual, position, Array(message),
      () => editor.getCaretModel.moveToLogicalPosition(position)
    )
  }
}

private[worksheet]
object WorksheetCompiler extends WorksheetPerFileConfig {

  private val EmptyRunnable: Runnable = () => {}
  private val ConfigErrorHeader = "Worksheet configuration error:"

  type EvaluationCallback = WorksheetCompilerResult => Unit

  sealed trait WorksheetCompilerResult
  object WorksheetCompilerResult {
    // worksheet was compiled without any errors (but warnings possible) and evaluated successfully
    object CompiledAndEvaluated extends WorksheetCompilerResult

    sealed trait WorksheetCompilerError extends WorksheetCompilerResult
    final case class PreprocessError(error: WorksheetPreprocessError) extends WorksheetCompilerError
    final case class PreconditionError(message: String) extends WorksheetCompilerError
    final case object CompilationError extends WorksheetCompilerError
    final case class EvaluationError(cause: Throwable) extends WorksheetCompilerError
    final case class ProcessTerminatedError(returnCode: Int, message: String) extends WorksheetCompilerError
    final case class RemoteServerConnectorError(error: RemoteServerConnectorResult.Error) extends WorksheetCompilerError
  }

  private val RunnerClassName = "org.jetbrains.plugins.scala.worksheet.MyWorksheetRunner"

  // this method is only used when run type is [[org.jetbrains.plugins.scala.worksheet.server.OutOfProcessServer]]
  private def executeWorksheet(name: String, scalaFile: ScalaFile, mainClassName: String, addToCp: String)
                              (callback: EvaluationCallback, worksheetPrinter: WorksheetEditorPrinter)
                              (implicit module: Module): Unit =
    invokeLater {
      try {
        val params: JavaParameters = createDefaultParameters(scalaFile, mainClassName, addToCp)
        val processHandler = params.createOSProcessHandler()
        setUpUiAndRun(processHandler, scalaFile)(callback, worksheetPrinter)
      } catch {
        case NonFatal(ex: Throwable) =>
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
                           (callback: EvaluationCallback, worksheetPrinter: WorksheetEditorPrinter): Unit = {
    val myProcessListener: ProcessAdapter = new ProcessAdapter {
      override def onTextAvailable(event: ProcessEvent, outputType: Key[_]) {
        val isStdOutput = ConsoleViewContentType.getConsoleViewType(outputType) == ConsoleViewContentType.NORMAL_OUTPUT
        if (isStdOutput) {
          val text = event.getText
          worksheetPrinter.processLine(text)
        }
      }

      override def processTerminated(event: ProcessEvent): Unit = {
        println(event)
        worksheetPrinter.flushBuffer()
        val result = event.getExitCode match {
          case 0  => WorksheetCompilerResult.CompiledAndEvaluated
          case rc => WorksheetCompilerResult.ProcessTerminatedError(rc, event.getText)
        }
        callback.apply(result)
      }
    }
    handler.addProcessListener(myProcessListener)
    handler.startNotify()
  }

  trait CompilerMessagesCollector {
    def collectedMessages: Seq[CompilerMessage]
  }

  class CompilerInterfaceImpl(
    task: CompilerTask,
    worksheetPrinter: WorksheetEditorPrinter,
    auto: Boolean = false
  ) extends CompilerInterface
    with CompilerMessagesConsumer
    with CompilerMessagesCollector {

    private val messages = mutable.ArrayBuffer[CompilerMessage]()
    private var hasCompilationErrors = false

    private val isUnitTestMode = ApplicationManager.getApplication.isUnitTestMode

    override def isCompiledWithErrors: Boolean = hasCompilationErrors

    override def collectedMessages: Seq[CompilerMessage] = messages

    override def message(message: CompilerMessage): Unit = {
      // for now we only need the compiler messages in unit tests
      if (isUnitTestMode) {
        messages += message
      }
      if (message.getCategory == CompilerMessageCategory.ERROR) {
        hasCompilationErrors = true
      }
      if (!auto) {
        task.addMessage(message)
      }
    }

    override def progress(text: String, done: Option[Float]): Unit = {
      if (auto) return
      val taskIndicator = ProgressManager.getInstance.getProgressIndicator

      if (taskIndicator != null) {
        taskIndicator.setText(text)
        done.foreach(d => taskIndicator.setFraction(d.toDouble))
      }
    }

    override def worksheetOutput(text: String): Unit =
      worksheetPrinter.processLine(text)

    override def trace(ex: Throwable): Unit = {
      val message = "\n" + ex.stackTraceText // stacktrace already contains thr.toString which contains message
      worksheetPrinter.internalError(message)
    }

    override def finish(): Unit = {
      if (!hasCompilationErrors) {
        worksheetPrinter.flushBuffer()
      }
    }
  }
}
