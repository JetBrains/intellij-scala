package org.jetbrains.plugins.scala.worksheet.actions.topmenu

import com.intellij.execution._
import com.intellij.execution.configurations.JavaParameters
import com.intellij.execution.process.{OSProcessHandler, ProcessAdapter, ProcessEvent}
import com.intellij.execution.ui.ConsoleViewContentType
import com.intellij.icons.AllIcons
import com.intellij.ide.util.EditorHelper
import com.intellij.openapi.actionSystem.{AnAction, AnActionEvent}
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.compiler.{CompileContext, CompileStatusNotification, CompilerManager}
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.keymap.{KeymapManager, KeymapUtil}
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.{JavaSdkType, JdkUtil}
import com.intellij.openapi.roots.ModuleRootManager
import com.intellij.openapi.util.Key
import com.intellij.psi.{PsiDocumentManager, PsiFile}
import javax.swing.Icon
import org.jetbrains.annotations.NotNull
import org.jetbrains.plugins.scala.ScalaBundle
import org.jetbrains.plugins.scala.extensions.{inWriteAction, invokeAndWait, invokeLater}
import org.jetbrains.plugins.scala.lang.psi.api.ScalaFile
import org.jetbrains.plugins.scala.project._
import org.jetbrains.plugins.scala.statistics.{FeatureKey, Stats}
import org.jetbrains.plugins.scala.worksheet.actions.topmenu.RunWorksheetAction.RunWorksheetActionError._
import org.jetbrains.plugins.scala.worksheet.processor.WorksheetCompiler
import org.jetbrains.plugins.scala.worksheet.processor.WorksheetCompiler.{WorksheetEvaluationError, WorksheetRunResult}
import org.jetbrains.plugins.scala.worksheet.runconfiguration.WorksheetCache
import org.jetbrains.plugins.scala.worksheet.server.WorksheetProcessManager
import org.jetbrains.plugins.scala.worksheet.settings.WorksheetExternalRunType.PlainRunType
import org.jetbrains.plugins.scala.worksheet.settings.{WorksheetCommonSettings, WorksheetFileSettings}

import scala.concurrent.{Future, Promise}
import scala.util.{Failure, Try}

class RunWorksheetAction extends AnAction with TopComponentAction {

  override def genericText: String = ScalaBundle.message("worksheet.execute.button")

  override def actionIcon: Icon = AllIcons.Actions.Execute

  override def shortcutId: Option[String] = Some("Scala.RunWorksheet")

  override def actionPerformed(e: AnActionEvent): Unit =
    RunWorksheetAction.runCompiler(e.getProject, auto = false)

  override def update(e: AnActionEvent): Unit = {
    super.update(e)

    val shortcuts = KeymapManager.getInstance.getActiveKeymap.getShortcuts("Scala.RunWorksheet")

    if (shortcuts.nonEmpty) {
      val shortcutText = s" (${KeymapUtil.getShortcutText(shortcuts(0))})"
      e.getPresentation.setText(ScalaBundle.message("worksheet.execute.button") + shortcutText)
    }
  }
}

object RunWorksheetAction {

  private val runnerClassName = "org.jetbrains.plugins.scala.worksheet.MyWorksheetRunner"

  sealed trait RunWorksheetActionError

  object RunWorksheetActionError {
    object NoModuleError extends RunWorksheetActionError
    object NoWorksheetFileError extends RunWorksheetActionError
    case class ProjectCompilationError(aborted: Boolean, errors: Int, warnings: Int, context: CompileContext) extends RunWorksheetActionError
    case class WorksheetCompilerError(error: WorksheetCompiler.WorksheetEvaluationError) extends RunWorksheetActionError
    case class ProcessTerminatedError(returnCode: Int, message: String) extends RunWorksheetActionError
  }

  def runCompiler(project: Project, auto: Boolean): Unit = {
    Stats.trigger(FeatureKey.runWorksheet)

    if (project == null) return

    val editor = FileEditorManager.getInstance(project).getSelectedTextEditor

    if (editor == null) return

    runCompiler(project, editor, auto)
  }

  def runCompiler(@NotNull project: Project, @NotNull editor: Editor, auto: Boolean): Future[Either[RunWorksheetActionError, Any]] = {
    val promise = Promise[Either[RunWorksheetActionError, Any]]()
    try {
      doRunCompiler(project,editor, auto)(promise)
    } catch {
      case ex: Exception =>
        promise.failure(ex)
    }
    promise.future
  }

  private def doRunCompiler(@NotNull project: Project, @NotNull editor: Editor, auto: Boolean)
                           (promise: Promise[Either[RunWorksheetActionError, Any]]): Unit = {

    val psiFile: PsiFile = PsiDocumentManager.getInstance(project).getPsiFile(editor.getDocument)
    WorksheetProcessManager.stop(psiFile.getVirtualFile)

    val file: ScalaFile = psiFile match {
      case file: ScalaFile if file.isWorksheetFile => file
      case _ =>
        promise.success(Left(NoWorksheetFileError))
        return
    }

    val fileSettings = WorksheetCommonSettings(file)
    implicit val module: Module = fileSettings.getModuleFor
    if (module == null) {
      promise.success(Left(NoModuleError))
      return
    }

    val viewer = WorksheetCache.getInstance(project).getViewer(editor)

    if (viewer != null && !WorksheetFileSettings.isRepl(file)) {
      invokeAndWait(ModalityState.any()) {
        inWriteAction {
          CleanWorksheetAction.resetScrollModel(viewer)
          if (!auto) {
            CleanWorksheetAction.cleanWorksheet(file.getNode, editor, viewer, project)
          }
        }
      }
    }

    def runnable(): Unit = {
      val callback: Either[WorksheetEvaluationError, WorksheetRunResult] => Unit = {
        case Right(WorksheetRunResult.CompileOutsideCompilerProcess(className, addToCp)) =>
          invokeLater {
            try {
              executeWorksheet(file.getName, file, className, addToCp)(promise)
            } catch {
              case ex: Throwable =>
                promise.failure(ex)
                throw ex
            }
          }
        case Right(other) => promise.success(Right(other))
        case Left(error) => promise.success(Left(WorksheetCompilerError(error)))
      }
      val compiler = new WorksheetCompiler(module, editor, file, callback, auto)
      compiler.compileAndRunFile()
    }

    if (fileSettings.isMakeBeforeRun) {
      val compilerNotification: CompileStatusNotification =
        (aborted: Boolean, errors: Int, warnings: Int, context: CompileContext) => {
          if (!aborted && errors == 0) {
            runnable()
          } else {
            promise.success(Left(ProjectCompilationError(aborted, errors, warnings, context)))
          }
        }
      CompilerManager.getInstance(project).make(module, compilerNotification)
    } else {
      runnable()
    }
  }


  /**
   * FYI: REPL mode works only if we use compiler server and run worksheet with "InProcess" setting
   * this method is only used when run type is [[org.jetbrains.plugins.scala.worksheet.server.OutOfProcessServer]]
   */
  private def executeWorksheet(name: String, scalaFile: ScalaFile, mainClassName: String, addToCp: String)
                              (promise: Promise[Either[RunWorksheetActionError, Any]])
                              (implicit module: Module): Unit = {
    val params: JavaParameters = createDefaultParameters(scalaFile, mainClassName, addToCp)
    val processHandler = params.createOSProcessHandler()
    setUpUiAndRun(processHandler, scalaFile)(promise)
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
    params.setMainClass(runnerClassName)
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
                           (promise: Promise[Either[RunWorksheetActionError, Any]]): Unit = {
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
          case 0  => Right(())
          case rc => Left(ProcessTerminatedError(rc, event.getText))
        }
        promise.success(result)
      }
    }

    handler.addProcessListener(myProcessListener)
    handler.startNotify()
  }
}