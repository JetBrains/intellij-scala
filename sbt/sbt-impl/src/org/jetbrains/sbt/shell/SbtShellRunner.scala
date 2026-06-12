package org.jetbrains.sbt.shell

import com.intellij.execution.Executor
import com.intellij.execution.configurations.RemoteConnection
import com.intellij.execution.console.*
import com.intellij.execution.process.{OSProcessHandler, ProcessHandler}
import com.intellij.execution.runners.AbstractConsoleRunnerWithHistory
import com.intellij.execution.ui.RunContentDescriptor
import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.application.{ApplicationManager, WriteIntentReadAction}
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.editor.ex.util.EditorUtil
import com.intellij.openapi.project.Project
import com.intellij.ui.content.impl.ContentImpl
import org.jetbrains.plugins.scala.extensions.{executeOnPooledThread, invokeLater}
import org.jetbrains.plugins.scala.isUnitTestMode
import org.jetbrains.plugins.scala.project.ProjectExt
import org.jetbrains.plugins.scala.statistics.SbtShellCommandsUsagesCollector
import org.jetbrains.sbt.SbtBundle
import org.jetbrains.sbt.icons.Icons

import java.util
import javax.swing.{Icon, JLabel, SwingConstants}
import scala.jdk.CollectionConverters.*

final class SbtShellRunner(
  project: Project,
  consoleTitle: String,
  debugConnection: Option[RemoteConnection],
  activateToolWindowOnStartup: Boolean = true,
)
  extends AbstractConsoleRunnerWithHistory[SbtShellConsoleView](project, consoleTitle, project.baseDir.getCanonicalPath) {

  private val log = Logger.getInstance(getClass)

  // lazy so that getProcessHandler will return something initialized when this is first accessed
  private lazy val myConsoleExecuteActionHandler: SbtShellExecuteActionHandler =
    new SbtShellExecuteActionHandler(getProcessHandler)

  // the process handler should only be used to listen to the running process!
  // SbtProcessManager is solely responsible for destroying/respawning
  // TODO: why is this lazy val? acquireShellProcessHandler can create a new process handler process data with
  //  new process handler, new data and new runner!!
  private lazy val myProcessHandler: OSProcessHandler =
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
    SbtShellToolWindowFactory.instance(using project).foreach { toolWindow =>
      if (isUnitTestMode) return

      invokeLater {
        val label = new JLabel(SbtBundle.message("initializing.sbt.shell.message"), SwingConstants.CENTER)
        label.setOpaque(true)
        //noinspection ScalaExtractStringToBundle
        SbtShellToolWindowFactory.setContent(toolWindow, new ContentImpl(label, "", false))
      }
    }
  }

  // is called from AbstractConsoleRunnerWithHistory.initAndRun from EDT, can be invoked asynchronously
  override def createConsoleView: SbtShellConsoleView = {
    log.debug("createConsoleView")
    SbtShellConsoleView(project, debugConnection)
  }

  // is called from AbstractConsoleRunnerWithHistory.initAndRun from EDT, can be invoked asynchronously
  override def createContentDescriptorAndActions(): Unit = {
    log.debug("createContentDescriptorAndActions")
    val callSuper: Runnable = () => super.createContentDescriptorAndActions()
    if (ApplicationManager.getApplication.isUnitTestMode) {
      // IJPL-27737
      // `com.intellij.openapi.editor.impl.SettingsImpl.reinitDocumentIndentOptions` is not
      // called within a read action in tests only.
      // A write-intent read action _must_ be used here. A regular read action causes a deadlock in tests.
      // TODO: remove this branch after a fix in the platform.
      //noinspection ApiStatus,UnstableApiUsage
      WriteIntentReadAction.run(callSuper)
    } else {
      callSuper.run()
    }

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

    // Do not initialize shell communication here: SbtProcessManager does it before startNotify, so prompt output cannot race ahead of the command queue listener.

    SbtShellToolWindowFactory.initUi(
      project,
      actionGroup = consoleView.createActionGroup(),
      component = consoleView.getComponent
    )
  }

  // TODO update icon with ready/working state
  private def shellPromptChanger(consoleView: SbtShellConsoleView): SbtShellReadyLineListener = {
    def scrollToEnd(): Unit = invokeLater {
      val editor = consoleView.getHistoryViewer
      if (!editor.isDisposed)
        EditorUtil.scrollToTheEnd(editor)
    }

    new SbtShellReadyLineListener(
      "prompt changer",
      whenReady = {
        consoleView.setPrompt(">")
        scrollToEnd()
      },
      whenWorking = {
        val status = SbtBundle.message("sbt.shell.status.busy")
        consoleView.setPrompt(s"($status) >")
        scrollToEnd()
      },
      project
    )
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
    SbtShellRunner.openShellOnStartup(
      activateToolWindowOnStartup,
      focus = contentDescriptor.isAutoFocusContent,
      openShell = openShell,
    )

  /** Shows ToolWindow on UI thread asynchronously */
  def openShell(focus: Boolean): Unit =
    SbtShellRunner.openShell(focus, project)

  object SbtShellRootType extends ConsoleRootType("sbt.shell", getConsoleTitle)

  class SbtShellExecuteActionHandler(processHandler: ProcessHandler)
    extends ProcessBackedConsoleExecuteActionHandler(processHandler, true) {

    // input is echoed to the process anyway
    setAddCurrentToHistory(false)

    override def execute(text: String, console: LanguageConsoleView): Unit = {
      SbtShellCommandsUsagesCollector.logShellCommand(project)
      if (isTestCommand(text)) {
        SbtShellCommandsUsagesCollector.logShellTestCommand(project)
      }

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
  private[shell] def openShellOnStartup(
    activateToolWindowOnStartup: Boolean,
    focus: Boolean,
    openShell: Boolean => Unit,
  ): Unit =
    if (activateToolWindowOnStartup) {
      openShell(focus)
    }

  /**
   * Shows ToolWindow on UI thread asynchronously.
   * This method doesn't start the sbt shell process.
   *
   * ATTENTION: Use this to show the sbt shell tool window when a shell runner is not available,
   * and acquiring a new runner would be inappropriate (it could start a new shell while one is shutting down or stopped).
   * Prefer `SbtShellRunner#openShell` when a runner is available, as this method is less safe.
   */
  private[shell] def openShell(focus: Boolean, project: Project): Unit =
    invokeLater {
      SbtShellToolWindowFactory.instance(using project).foreach { toolWindow =>
        toolWindow.activate(null, focus)
      }
    }
}
