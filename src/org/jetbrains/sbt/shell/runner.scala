package org.jetbrains.sbt.shell

import java.awt.event.KeyEvent
import java.util
import javax.swing.Icon

import com.intellij.execution.console._
import com.intellij.execution.filters.UrlFilter.UrlFilterProvider
import com.intellij.execution.filters._
import com.intellij.execution.process.{OSProcessHandler, ProcessHandler}
import com.intellij.execution.runners.AbstractConsoleRunnerWithHistory
import com.intellij.execution.ui.RunContentDescriptor
import com.intellij.execution.ui.actions.CloseAction
import com.intellij.execution.{ExecutionManager, Executor}
import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem._
import com.intellij.openapi.project.{DumbAwareAction, Project}
import com.intellij.psi.search.GlobalSearchScope

import scala.collection.JavaConverters._

/**
  * Created by jast on 2016-5-29.
  */
class SbtShellRunner(project: Project, consoleTitle: String)
  extends AbstractConsoleRunnerWithHistory[LanguageConsoleImpl](project, consoleTitle, project.getBaseDir.getCanonicalPath) {

  private val myConsoleView: LanguageConsoleImpl = {
    val cv = new LanguageConsoleImpl(project, SbtShellFileType.getName, SbtShellLanguage)
    cv.getConsoleEditor.setOneLineMode(true)

    // exception file links
    cv.addMessageFilter(new ExceptionFilter(GlobalSearchScope.allScope(project)))

    // url links
    new UrlFilterProvider().getDefaultFilters(project).foreach(cv.addMessageFilter)

    // file links
    val patternMacro = s"\\s${RegexpFilter.FILE_PATH_MACROS}:${RegexpFilter.LINE_MACROS}:\\s"
    val pattern = new RegexpFilter(project, patternMacro).getPattern
    val format = new PatternHyperlinkFormat(pattern, false, false, PatternHyperlinkPart.PATH, PatternHyperlinkPart.LINE)
    val dataFinder = new PatternBasedFileHyperlinkRawDataFinder(Array(format))
    val fileFilter = new PatternBasedFileHyperlinkFilter(project, null, dataFinder)
    cv.addMessageFilter(fileFilter)

    cv
  }

  private lazy val processComponent = SbtProcessManager.forProject(project)

  // lazy so that getProcessHandler will return something initialized when this is first accessed
  private lazy val myConsoleExecuteActionHandler: SbtShellExecuteActionHandler =
    new SbtShellExecuteActionHandler(getProcessHandler)

  // the process handler should only be used to access the running process!
  // SbtProcessComponent is solely responsible for destroying/respawning
  private lazy val myProcessHandler = processComponent.acquireShellProcessHandler

  override def createProcessHandler(process: Process): OSProcessHandler = myProcessHandler

  override def createConsoleView(): LanguageConsoleImpl = myConsoleView

  override def createProcess(): Process = myProcessHandler.getProcess


  object SbtShellRootType extends ConsoleRootType("sbt.shell", getConsoleTitle)

  override def createExecuteActionHandler(): SbtShellExecuteActionHandler = {
    val historyController = new ConsoleHistoryController(SbtShellRootType, null, getConsoleView)
    historyController.install()

    myConsoleExecuteActionHandler
  }


  override def fillToolBarActions(toolbarActions: DefaultActionGroup,
                                  defaultExecutor: Executor,
                                  contentDescriptor: RunContentDescriptor): util.List[AnAction] = {

    val myToolbarActions = List(
      new RestartAction(this, defaultExecutor, contentDescriptor),
      new CloseAction(defaultExecutor, contentDescriptor, project),
      new ExecuteTaskAction("products", Option(AllIcons.Actions.Compile))
    )

    val allActions = List(
      createAutoCompleteAction(),
      createConsoleExecAction(myConsoleExecuteActionHandler)
    ) ++ myToolbarActions

    toolbarActions.addAll(myToolbarActions.asJava)
    allActions.asJava
  }


  def createAutoCompleteAction(): AnAction = {
    val action = new AutoCompleteAction
    action.registerCustomShortcutSet(KeyEvent.VK_TAB, 0, null)
    action.getTemplatePresentation.setVisible(false)
    action
  }

  /** A new instance of the runner with the same constructor params as this one, but fresh state. */
  def respawn: SbtShellRunner = {
    processComponent.restartProcess()
    new SbtShellRunner(project, consoleTitle)
  }

}

object SbtShellRunner {

  /** Initialize and run an sbt shell window. */
  def run(project: Project): Unit = {
    val title = "SBT Shell"
    val cr = new SbtShellRunner(project, title)
    cr.initAndRun()
  }
}

class AutoCompleteAction extends DumbAwareAction {
  override def actionPerformed(e: AnActionEvent): Unit = {
    // TODO call code completion (ctrl+space by default)
  }
}


class RestartAction(runner: SbtShellRunner, executor: Executor, contentDescriptor: RunContentDescriptor) extends DumbAwareAction {
  copyFrom(ActionManager.getInstance.getAction(IdeActions.ACTION_RERUN))

  val templatePresentation: Presentation = getTemplatePresentation
  templatePresentation.setIcon(AllIcons.Actions.Restart)
  templatePresentation.setText("Restart SBT Shell") // TODO i18n / language-bundle
  templatePresentation.setDescription(null)

  def actionPerformed(e: AnActionEvent): Unit = {
    ExecutionManager.getInstance(runner.getProject)
      .getContentManager
      .removeRunContent(executor, contentDescriptor)

    runner.respawn.initAndRun()
  }

  override def update(e: AnActionEvent) {}
}

class SbtShellExecuteActionHandler(processHandler: ProcessHandler)
  extends ProcessBackedConsoleExecuteActionHandler(processHandler, true) {
}

class ExecuteTaskAction(task: String, icon: Option[Icon]) extends DumbAwareAction {

  getTemplatePresentation.setIcon(icon.orNull)
  getTemplatePresentation.setText(s"Execute $task")

  override def actionPerformed(e: AnActionEvent): Unit = {
    new SbtShellCommunication(e.getProject).task(task)
  }
}
