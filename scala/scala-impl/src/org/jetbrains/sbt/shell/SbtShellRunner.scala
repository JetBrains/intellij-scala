package org.jetbrains.sbt.shell

import java.awt.BorderLayout
import java.util

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
import com.intellij.ui.content.impl.ContentImpl
import com.intellij.ui.content.{Content, ContentFactory}
import javax.swing.{Icon, JLabel, JPanel, SwingConstants}
import org.jetbrains.plugins.scala.extensions.{executeOnPooledThread, invokeLater}
import org.jetbrains.plugins.scala.icons.Icons
import org.jetbrains.plugins.scala.macroAnnotations.TraceWithLogger
import org.jetbrains.plugins.scala.project.ProjectExt
import org.jetbrains.plugins.scala.statistics.{FeatureKey, Stats}
import org.jetbrains.sbt.SbtBundle
import org.jetbrains.sbt.shell.SbtShellRunner._

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
  @TraceWithLogger
  override def initAndRun(): Unit = {
    showInitializingPlaceholder()
    super.initAndRun()
  }

  @TraceWithLogger
  private def showInitializingPlaceholder(): Unit = {
    SbtShellToolWindowFactory.instance(project).foreach { toolWindow =>
      invokeLater {
        val label = new JLabel(SbtBundle.message("initializing.sbt.shell.message"), SwingConstants.CENTER)
        label.setOpaque(true)
        //noinspection ScalaExtractStringToBundle
        toolWindow.setContent(new ContentImpl(label, "", false))
      }
    }
  }

  // is called from AbstractConsoleRunnerWithHistory.initAndRun from EDT, can be invoked asynchronously
  @TraceWithLogger
  override def createConsoleView: SbtShellConsoleView = {
    SbtShellConsoleView(project, debugConnection)
  }

  // is called from AbstractConsoleRunnerWithHistory.initAndRun from EDT, can be invoked asynchronously
  @TraceWithLogger
  override def createContentDescriptorAndActions(): Unit = if(notInTest) {
    super.createContentDescriptorAndActions()

    executeOnPooledThread {
      initSbtShell()
    }
  }

  @TraceWithLogger
  private def initSbtShell(): Unit = {
    val consoleView = getConsoleView
    if (consoleView == null) {
      log.error("console view should be created in initAndRun by this moment")
      return
    }

    consoleView.setPrompt("(initializing) >")

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
      whenReady = if (notInTest) {
        consoleView.setPrompt(">")
        scrollToEnd()
      },
      whenWorking = if (notInTest) {
        consoleView.setPrompt("(busy) >")
        scrollToEnd()
      }
    )
  }

  private def initSbtShellUi(consoleView: SbtShellConsoleView): Unit = if (notInTest) {
    SbtShellToolWindowFactory.instance(project).foreach { toolWindow =>
      invokeLater {
        val content = createToolWindowContent(consoleView)
        toolWindow.setContent(content)
      }
    }
  }

  @TraceWithLogger
  private def createToolWindowContent(consoleView: SbtShellConsoleView): Content = {
    val actionGroup = consoleView.createActionGroup()
    val actionToolBar = ActionManager.getInstance().createActionToolbar("sbt-shell-toolbar", actionGroup, false)

    val toolbarPanel = new JPanel()
    toolbarPanel.setLayout(new BorderLayout)
    toolbarPanel.add(actionToolBar.getComponent)

    val mainPanel = new JPanel()
    mainPanel.setLayout(new BorderLayout)
    mainPanel.add(toolbarPanel, BorderLayout.WEST)
    mainPanel.add(consoleView.getComponent, BorderLayout.CENTER)

    //noinspection ScalaExtractStringToBundle
    val content = ContentFactory.SERVICE.getInstance.createContent(mainPanel, "sbt-shell-toolwindow-content", true)
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
      Stats.trigger(FeatureKey.sbtShellCommand)
      Stats.trigger(isTestCommand(text), FeatureKey.sbtShellTestCommand)

      EditorUtil.scrollToTheEnd(console.getHistoryViewer)
      super.execute(text, console)
    }

    private def isTestCommand(line: String): Boolean = {
      val trimmed = line.trim
      trimmed == "test" || trimmed.startsWith("testOnly") || trimmed.startsWith("testQuick")
    }
  }
}

object SbtShellRunner {

  private def notInTest: Boolean = !ApplicationManager.getApplication.isUnitTestMode
}