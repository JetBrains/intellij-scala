package org.jetbrains.sbt.shell

import java.util
import javax.swing.Icon

import com.intellij.execution.Executor
import com.intellij.execution.configurations.RemoteConnection
import com.intellij.execution.console._
import com.intellij.execution.process.{OSProcessHandler, ProcessHandler}
import com.intellij.execution.runners.AbstractConsoleRunnerWithHistory
import com.intellij.execution.ui.layout.PlaceInGrid
import com.intellij.execution.ui.{RunContentDescriptor, RunnerLayoutUi}
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem._
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.editor.ex.util.EditorUtil
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.wm.{ToolWindow, ToolWindowManager}
import com.intellij.ui.content.{Content, ContentFactory}
import com.pty4j.{PtyProcess, WinSize}
import org.jetbrains.plugins.scala.icons.Icons
import org.jetbrains.annotations.NotNull

import scala.collection.JavaConverters._
import SbtShellRunner._
import com.pty4j.unix.UnixPtyProcess
/**
  * Created by jast on 2016-5-29.
  */
class SbtShellRunner(project: Project, consoleTitle: String, debugConnection: Option[RemoteConnection])
  extends AbstractConsoleRunnerWithHistory[LanguageConsoleImpl](project, consoleTitle, project.getBaseDir.getCanonicalPath)
  with Disposable
{

  private val toolWindowTitle = project.getName

  private lazy val myConsoleView: LanguageConsoleImpl =
    ShellUIUtil.inUIsync {
      val cv = SbtShellConsoleView(project, debugConnection)
      Disposer.register(this, cv)
      cv
    }

  // lazy so that getProcessHandler will return something initialized when this is first accessed
  private lazy val myConsoleExecuteActionHandler: SbtShellExecuteActionHandler =
    new SbtShellExecuteActionHandler(getProcessHandler)

  // the process handler should only be used to listen to the running process!
  // SbtProcessManager is solely responsible for destroying/respawning
  private lazy val myProcessHandler =
    SbtProcessManager.forProject(project).acquireShellProcessHandler

  override def createProcessHandler(process: Process): OSProcessHandler = myProcessHandler

  override def createConsoleView(): LanguageConsoleImpl = myConsoleView

  override def createProcess(): Process = myProcessHandler.getProcess

  //don't init UI for unit tests
  override def createContentDescriptorAndActions(): Unit =
    if (notInTest) super.createContentDescriptorAndActions()

  override def initAndRun(): Unit = {
    super.initAndRun()
    import ShellUIUtil.inUI
    inUI {

      // on Windows the terminal defaults to 80 columns which wraps and breaks highlighting.
      // Use a wider value that should be reasonable in most cases. Has no effect on Unix.
      // TODO perhaps determine actual width of window and adapt accordingly
      if (notInTest) {
        myProcessHandler.getProcess match {
          case _: UnixPtyProcess => // don't need to do stuff
          case proc: PtyProcess => proc.setWinSize(new WinSize(2000, 100))
        }
      }

      // assume initial state is Working
      // this is not correct when shell process was started without view, but we avoid that
      myConsoleView.setPrompt("(initializing) >")

      // TODO update icon with ready/working state
      val shellPromptChanger = new SbtShellReadyListener(
        whenReady = if (notInTest) myConsoleView.setPrompt(">"),
        whenWorking = if (notInTest) myConsoleView.setPrompt("(busy) >")
      )

      def scrollToEnd(): Unit = inUI {
        val editor = getConsoleView.getHistoryViewer
        if (!editor.isDisposed)
          EditorUtil.scrollToTheEnd(editor)
      }

      val scrollOnStateChange = new SbtShellReadyListener(
        whenReady = scrollToEnd(),
        whenWorking = scrollToEnd()
      )
      myProcessHandler.addProcessListener(shellPromptChanger)
      myProcessHandler.addProcessListener(scrollOnStateChange)
      SbtShellCommunication.forProject(project).initCommunication(myProcessHandler)

      if (notInTest) {
        val twm = ToolWindowManager.getInstance(project)
        val toolWindow = twm.getToolWindow(SbtShellToolWindowFactory.ID)
        val content = createToolWindowContent
        addToolWindowContent(toolWindow, content)
      }
    }
  }

  override def createExecuteActionHandler(): SbtShellExecuteActionHandler = {
    val historyController = new ConsoleHistoryController(SbtShellRootType, null, getConsoleView)
    historyController.install()

    myConsoleExecuteActionHandler
  }

  override def fillToolBarActions(toolbarActions: DefaultActionGroup,
                                  defaultExecutor: Executor,
                                  contentDescriptor: RunContentDescriptor): util.List[AnAction] = {

    // the actual toolbar actions are initialized handled by SbtShellToolWindowFactory because this is just a hackjob
    // the exec action needs to be created here so it is registered. TODO refactor so we don't need this
    List(createConsoleExecAction(myConsoleExecuteActionHandler)).asJava
  }

  override def getConsoleIcon: Icon = Icons.SBT_SHELL

  override def showConsole(defaultExecutor: Executor, contentDescriptor: RunContentDescriptor): Unit = {
    val twm = ToolWindowManager.getInstance(getProject)
    val toolWindow = twm.getToolWindow(SbtShellToolWindowFactory.ID)
    twm.getFocusManager.requestFocusInProject(toolWindow.getComponent, project)
  }

  def openShell(focus: Boolean): Unit = {
    val toolWindow = ToolWindowManager.getInstance(project).getToolWindow(SbtShellToolWindowFactory.ID)
    toolWindow.activate(null, focus)
    val content = toolWindow.getContentManager.findContent(toolWindowTitle)
    if (content != null)
      toolWindow.getContentManager.setSelectedContent(content, focus)
  }

  private def addToolWindowContent(@NotNull toolWindow: ToolWindow, @NotNull content: Content): Unit = {
    val twContentManager = toolWindow.getContentManager
    twContentManager.removeAllContents(true)
    twContentManager.addContent(content)
  }

  private def createToolWindowContent: Content = {
    //Create runner UI layout
    val factory = RunnerLayoutUi.Factory.getInstance(project)
    val layoutUi = factory.create("sbt-shell-toolwindow-runner", "", "session", project)
    val layoutComponent = layoutUi.getComponent
    // Adding actions
    val group = new DefaultActionGroup
    layoutUi.getOptions.setLeftToolbar(group, ActionPlaces.UNKNOWN)
    val console = layoutUi.createContent(SbtShellToolWindowFactory.ID, myConsoleView.getComponent, "sbt-shell-toolwindow-console", null, null)

    myConsoleView.createConsoleActions.foreach(group.add)

    layoutUi.addContent(console, 0, PlaceInGrid.right, false)

    val content = ContentFactory.SERVICE.getInstance.createContent(layoutComponent, "sbt-shell-toolwindow-content", true)
    content.setTabName(toolWindowTitle)
    content.setDisplayName(toolWindowTitle)
    content.setToolwindowTitle(toolWindowTitle)

    content
  }

  override def dispose(): Unit = {}

  object SbtShellRootType extends ConsoleRootType("sbt.shell", getConsoleTitle)

  class SbtShellExecuteActionHandler(processHandler: ProcessHandler)
    extends ProcessBackedConsoleExecuteActionHandler(processHandler, true) {

    // input is echoed to the process anyway
    setAddCurrentToHistory(false)

    override def execute(text: String, console: LanguageConsoleView): Unit = {
      EditorUtil.scrollToTheEnd(console.getHistoryViewer)
      super.execute(text, console)
    }
  }
}

object SbtShellRunner {
  private def notInTest = ! ApplicationManager.getApplication.isUnitTestMode
}