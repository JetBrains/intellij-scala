package org.jetbrains.sbt.shell

import java.awt.event.KeyEvent
import java.util
import javax.swing.Icon

import com.intellij.execution.Executor
import com.intellij.execution.console._
import com.intellij.execution.filters.UrlFilter.UrlFilterProvider
import com.intellij.execution.filters._
import com.intellij.execution.process.{OSProcessHandler, ProcessHandler}
import com.intellij.execution.runners.AbstractConsoleRunnerWithHistory
import com.intellij.execution.ui.RunContentDescriptor
import com.intellij.execution.ui.actions.CloseAction
import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem._
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.psi.search.GlobalSearchScope
import com.pty4j.{PtyProcess, WinSize}
import org.jetbrains.plugins.scala.icons.Icons
import org.jetbrains.sbt.project.structure.SbtRunner
import org.jetbrains.sbt.shell.action.{AutoCompleteAction, ExecuteTaskAction, RestartAction}

import scala.collection.JavaConverters._

/**
  * Created by jast on 2016-5-29.
  */
class SbtShellRunner(project: Project, consoleTitle: String)
  extends AbstractConsoleRunnerWithHistory[LanguageConsoleImpl](project, consoleTitle, project.getBaseDir.getCanonicalPath) {

  private val myConsoleView: LanguageConsoleImpl = ShellUIUtil.inUIsync {
    val cv = new LanguageConsoleImpl(project, SbtShellFileType.getName, SbtShellLanguage)
    cv.getConsoleEditor.setOneLineMode(true)

    // exception file links
    cv.addMessageFilter(new ExceptionFilter(GlobalSearchScope.allScope(project)))

    // url links
    new UrlFilterProvider().getDefaultFilters(project).foreach(cv.addMessageFilter)

    // file links
    val patternMacro = s"${RegexpFilter.FILE_PATH_MACROS}:${RegexpFilter.LINE_MACROS}:\\s"
    val pattern = new RegexpFilter(project, patternMacro).getPattern
    import PatternHyperlinkPart._
    // FILE_PATH_MACROS includes a capturing group at the beginning that the format only can handle if the first linkPart is null
    val format = new PatternHyperlinkFormat(pattern, false, false, null, PATH, LINE)
    val dataFinder = new PatternBasedFileHyperlinkRawDataFinder(Array(format))
    val fileFilter = new PatternBasedFileHyperlinkFilter(project, null, dataFinder)
    cv.addMessageFilter(fileFilter)

    cv
  }

  private lazy val processManager = SbtProcessManager.forProject(project)

  // lazy so that getProcessHandler will return something initialized when this is first accessed
  private lazy val myConsoleExecuteActionHandler: SbtShellExecuteActionHandler =
    new SbtShellExecuteActionHandler(getProcessHandler)

  // the process handler should only be used to listen to the running process!
  // SbtProcessComponent is solely responsible for destroying/respawning
  private lazy val myProcessHandler = processManager.acquireShellProcessHandler

  override def createProcessHandler(process: Process): OSProcessHandler = myProcessHandler

  override def createConsoleView(): LanguageConsoleImpl = myConsoleView

  override def createProcess(): Process = myProcessHandler.getProcess

  //don't init UI for unit tests
  override def createContentDescriptorAndActions(): Unit =
    if (!ApplicationManager.getApplication.isUnitTestMode) super.createContentDescriptorAndActions()

  override def initAndRun(): Unit = {
    super.initAndRun()
    ShellUIUtil.inUI {

      // on Windows the terminal defaults to 80 columns which wraps and breaks highlighting.
      // Use a wider value that should be reasonable in most cases. Has no effect on Unix.
      // TODO perhaps determine actual width of window and adapt accordingly
      myProcessHandler.getProcess match {
        case proc: PtyProcess => proc.setWinSize(new WinSize (2000, 100))
      }

      // assume initial state is Working
      // this is not correct when shell process was started without view, but we avoid that
      myConsoleView.setPrompt("X")

      // TODO update icon with ready/working state
      val shellPromptChanger = new SbtShellReadyListener(
        whenReady = if (!SbtRunner.isInTest) myConsoleView.setPrompt(">"),
        whenWorking = if (!SbtRunner.isInTest) myConsoleView.setPrompt("X")
      )
      SbtProcessManager.forProject(project).attachListener(shellPromptChanger)
      SbtShellCommunication.forProject(project).initCommunication(myProcessHandler)
    }
  }

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

  override def getConsoleIcon: Icon = Icons.SBT_SHELL

  def openShell(focus: Boolean): Unit = {
    val toolWindow = ToolWindowManager.getInstance(project).getToolWindow(getExecutor.getToolWindowId)
    toolWindow.activate(null, focus)
    val content = toolWindow.getContentManager.findContent(consoleTitle)
    if (content != null)
      toolWindow.getContentManager.setSelectedContent(content, focus)
  }

  def createAutoCompleteAction(): AnAction = {
    val action = new AutoCompleteAction // TODO some sensible implementation possible yet?
    action.registerCustomShortcutSet(KeyEvent.VK_TAB, 0, null)
    action.getTemplatePresentation.setVisible(false)
    action
  }
}

class SbtShellExecuteActionHandler(processHandler: ProcessHandler)
  extends ProcessBackedConsoleExecuteActionHandler(processHandler, true) {

  // input is echoed to the process anyway
  setAddCurrentToHistory(false)
}