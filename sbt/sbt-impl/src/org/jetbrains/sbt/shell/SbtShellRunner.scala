package org.jetbrains.sbt.shell

import com.intellij.execution.Executor
import com.intellij.execution.configurations.RemoteConnection
import com.intellij.execution.console._
import com.intellij.execution.process.{ColoredProcessHandler, OSProcessHandler, ProcessHandler}
import com.intellij.execution.runners.AbstractConsoleRunnerWithHistory
import com.intellij.execution.ui.RunContentDescriptor
import com.intellij.openapi.actionSystem._
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.editor.ex.util.EditorUtil
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindow
import com.intellij.ui.content.impl.ContentImpl
import com.intellij.ui.content.{Content, ContentFactory}
import org.jetbrains.annotations.NotNull
import org.jetbrains.plugins.scala.extensions.{executeOnPooledThread, invokeLater}
import org.jetbrains.plugins.scala.project.ProjectExt
import org.jetbrains.plugins.scala.statistics.ScalaSbtUsagesCollector
import org.jetbrains.sbt.SbtBundle
import org.jetbrains.sbt.icons.Icons
import org.jetbrains.sbt.shell.SbtShellRunner._

import java.awt.BorderLayout
import java.util
import javax.swing.{Icon, JLabel, JPanel, SwingConstants}
import scala.jdk.CollectionConverters._

final class SbtShellRunner(project: Project, consoleTitle: String, debugConnection: Option[RemoteConnection])
  extends AbstractConsoleRunnerWithHistory[SbtShellConsoleView](project, consoleTitle, project.baseDir.getCanonicalPath) {

  private val log = Logger.getInstance(getClass)

  // lazy so that getProcessHandler will return something initialized when this is first accessed
  private lazy val myConsoleExecuteActionHandler: SbtShellExecuteActionHandler =
    new SbtShellExecuteActionHandler(getProcessHandler)

  // the process handler should only be used to listen to the running process!
  // SbtProcessManager is solely responsible for destroying/respawning
  // TODO: why is this lazy val? acquireShellProcessHandler can create a new process handler process data with
  //  new process handler, new data and new runner!!
  private lazy val myProcessHandler: ColoredProcessHandler =
    SbtProcessManager.forProject(project)
      .acquireShellProcessHandler()

  // is called from AbstractConsoleRunnerWithHistory.initAndRun synchronously
  override def createProcess: Process = myProcessHandler.getProcess

  // TODO: why is ignored? rethink API
  override def createProcessHandler(ignored : Process): OSProcessHandler = myProcessHandler
  def createProcessHandler: OSProcessHandler = createProcessHandler(null)

  //called manually by Scala Plugin, underlying initialization can be done asynchronously, so
  //right after the method execution `getConsoleView` can still return `null` and `isRunning` return false
  override def initAndRun(): Unit = {
    log.debug("initAndRun")
    showInitializingPlaceholder()
    super.initAndRun()
  }

  private def showInitializingPlaceholder(): Unit = {
    SbtShellToolWindowFactory.instance(project).foreach { toolWindow =>
      invokeLater {
        val label = new JLabel(SbtBundle.message("initializing.sbt.shell.message"), SwingConstants.CENTER)
        label.setOpaque(true)
        //noinspection ScalaExtractStringToBundle
        setContent(toolWindow, new ContentImpl(label, "", false))
      }
    }
  }

  // is called from AbstractConsoleRunnerWithHistory.initAndRun from EDT, can be invoked asynchronously
  override def createConsoleView: SbtShellConsoleView = {
    log.debug("createConsoleView")
    SbtShellConsoleView(project, debugConnection)
  }

  // is called from AbstractConsoleRunnerWithHistory.initAndRun from EDT, can be invoked asynchronously
  override def createContentDescriptorAndActions(): Unit = if(!isUnitTestMode) {
    log.debug("createContentDescriptorAndActions")
    super.createContentDescriptorAndActions()

    executeOnPooledThread {
      initSbtShell()
    }
  }

  private def initSbtShell(): Unit = {
    log.debug("initSbtShell")

    val consoleView = getConsoleView
    if (consoleView == null) {
      log.error("console view should be created in initAndRun by this moment")
      return
    }

    val status = SbtBundle.message("sbt.shell.status.initializing")
    consoleView.setPrompt(s"($status) >")

    myProcessHandler.addProcessListener(shellPromptChanger(consoleView))

    SbtShellCommunication.forProject(project).initCommunication(myProcessHandler)

    initSbtShellUi(consoleView)
  }

  // TODO update icon with ready/working state
  private def shellPromptChanger(consoleView: SbtShellConsoleView): SbtShellReadyListener = {
    def scrollToEnd(): Unit = invokeLater {
      val editor = consoleView.getHistoryViewer
      if (!editor.isDisposed)
        EditorUtil.scrollToTheEnd(editor)
    }

    new SbtShellReadyListener(
      "prompt changer",
      whenReady = if (!isUnitTestMode) {
        consoleView.setPrompt(">")
        scrollToEnd()
      },
      whenWorking = if (!isUnitTestMode) {
        val status = SbtBundle.message("sbt.shell.status.busy")
        consoleView.setPrompt(s"($status) >")
        scrollToEnd()
      }
    )
  }

  private def initSbtShellUi(consoleView: SbtShellConsoleView): Unit = if (!isUnitTestMode) {
    SbtShellToolWindowFactory.instance(project).foreach { toolWindow =>
      invokeLater {
        val content = createToolWindowContent(consoleView)
        setContent(toolWindow, content)
      }
    }
  }

  private def createToolWindowContent(consoleView: SbtShellConsoleView): Content = {
    log.debug("createToolWindowContent")

    val actionGroup = consoleView.createActionGroup()
    val actionToolBar = ActionManager.getInstance().createActionToolbar("sbt-shell-toolbar", actionGroup, false)

    val toolbarPanel = new JPanel()
    toolbarPanel.setLayout(new BorderLayout)
    toolbarPanel.add(actionToolBar.getComponent)

    val mainPanel = new JPanel()
    mainPanel.setLayout(new BorderLayout)
    mainPanel.add(toolbarPanel, BorderLayout.WEST)
    mainPanel.add(consoleView.getComponent, BorderLayout.CENTER)
    actionToolBar.setTargetComponent(mainPanel)

    //noinspection ScalaExtractStringToBundle
    val content = ContentFactory.getInstance.createContent(mainPanel, "sbt-shell-toolwindow-content", true)
    val toolWindowTitle = project.getName
    content.setTabName(toolWindowTitle)
    content.setDisplayName(toolWindowTitle)
    content.setToolwindowTitle(toolWindowTitle)

    content
  }


  override def createExecuteActionHandler(): SbtShellExecuteActionHandler = {
    val historyController = new ConsoleHistoryController(SbtShellRootType, null, getConsoleView)
    historyController.install()

    myConsoleExecuteActionHandler
  }

  override def fillToolBarActions(toolbarActions: DefaultActionGroup,
                                  defaultExecutor: Executor,
                                  contentDescriptor: RunContentDescriptor): util.List[AnAction] = {

    // the actual toolbar actions are created in SbtShellConsoleView because this is a hackjob
    // the exec action needs to be created here so it is registered. TODO refactor so we don't need this
    List(createConsoleExecAction(myConsoleExecuteActionHandler)).asJava
  }

  override def getConsoleIcon: Icon = Icons.SBT_SHELL

  override def showConsole(defaultExecutor: Executor, contentDescriptor: RunContentDescriptor): Unit =
    openShell(contentDescriptor.isAutoFocusContent)

  /** Shows ToolWindow on UI thread asynchronously */
  def openShell(focus: Boolean): Unit =
    invokeLater {
      SbtShellToolWindowFactory.instance(project).foreach { toolWindow =>
        toolWindow.activate(null, focus)
      }
    }

  def getDebugConnection: Option[RemoteConnection] = debugConnection

  object SbtShellRootType extends ConsoleRootType("sbt.shell", getConsoleTitle)

  class SbtShellExecuteActionHandler(processHandler: ProcessHandler)
    extends ProcessBackedConsoleExecuteActionHandler(processHandler, true) {

    // input is echoed to the process anyway
    setAddCurrentToHistory(false)

    override def execute(text: String, console: LanguageConsoleView): Unit = {
      ScalaSbtUsagesCollector.logShellCommand(project)
      if (isTestCommand(text)) {
        ScalaSbtUsagesCollector.logShellTestCommand(project)
      }

      EditorUtil.scrollToTheEnd(console.getHistoryViewer)
      super.execute(text, console)
    }

    private def isTestCommand(line: String): Boolean = {
      val trimmed = line.trim
      trimmed == "test" || trimmed.startsWith("testOnly") || trimmed.startsWith("testQuick")
    }
  }

  private def setContent(toolWindow: ToolWindow, @NotNull content: Content): Unit = {
    log.trace("setContent")
    val twContentManager = toolWindow.getContentManager
    twContentManager.removeAllContents(true)
    twContentManager.addContent(content)
  }
}

object SbtShellRunner {

  private val isUnitTestMode: Boolean = ApplicationManager.getApplication.isUnitTestMode
}