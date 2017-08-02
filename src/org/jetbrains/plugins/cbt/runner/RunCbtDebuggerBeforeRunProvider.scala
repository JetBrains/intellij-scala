package org.jetbrains.plugins.cbt.runner

import com.intellij.execution.application.ApplicationConfiguration
import com.intellij.execution.configurations.RunConfiguration
import com.intellij.execution.executors.DefaultRunExecutor
import com.intellij.execution.impl.{DefaultJavaProgramRunner, RunManagerImpl, RunnerAndConfigurationSettingsImpl}
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.execution.{BeforeRunTask, BeforeRunTaskProvider, ExecutionManager}
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.util.Key
import com.intellij.util.concurrency.Semaphore

class RunCbtDebuggerBeforeRunProvider(taskName: String, workingDir: String)
  extends BeforeRunTaskProvider[RunCbtDebuggerBeforeRunTask] {
  override def getId: Key[RunCbtDebuggerBeforeRunTask] = RunCbtDebuggerBeforeRunProvider.ID

  override def getName: String = RunCbtDebuggerBeforeRunProvider.NAME

  override def isConfigurable: Boolean = false

  override def getDescription(task: RunCbtDebuggerBeforeRunTask): String = RunCbtDebuggerBeforeRunProvider.NAME

  override def canExecuteTask(configuration: RunConfiguration, task: RunCbtDebuggerBeforeRunTask): Boolean =
    task.isInstanceOf[RunCbtDebuggerBeforeRunTask]

  override def configureTask(runConfiguration: RunConfiguration, task: RunCbtDebuggerBeforeRunTask): Boolean = false

  override def createTask(runConfiguration: RunConfiguration): RunCbtDebuggerBeforeRunTask =
    new RunCbtDebuggerBeforeRunTask(taskName, workingDir)

  override def executeTask(ctx: DataContext, c: RunConfiguration,
                           env: ExecutionEnvironment, beforeTunTask: RunCbtDebuggerBeforeRunTask): Boolean = {
    val project = env.getProject
    val finished = new Semaphore

    val listener = new CbtProcessListener {
      override def onComplete(): Unit = ()
      override def onTextAvailable(text: String, stderr: Boolean): Unit = {
        if (text contains "Listening for transport") {
          finished.up()
        }
      }
    }

    val configuration = new CbtBuildConfigurationFactory(beforeTunTask.taskName, true, beforeTunTask.workingDir, Seq("-debug"), CbtConfigurationType.getInstance, Some(listener))
      .createTemplateConfiguration(project)
    val runnerSettings = new RunnerAndConfigurationSettingsImpl(RunManagerImpl.getInstanceImpl(project), configuration)
    val environment = new ExecutionEnvironment(DefaultRunExecutor.getRunExecutorInstance, DefaultJavaProgramRunner.getInstance, runnerSettings, project)
    ExecutionManager.getInstance(project).restartRunProfile(environment)

    finished.waitFor()
    true
  }

}
object RunCbtDebuggerBeforeRunProvider {
  lazy val ID: Key[RunCbtDebuggerBeforeRunTask] = Key.create(NAME)
  val NAME = "CBT Debug"
}
class RunCbtDebuggerBeforeRunTask(val taskName: String, val workingDir: String)
  extends BeforeRunTask[RunCbtDebuggerBeforeRunTask](RunCbtDebuggerBeforeRunProvider.ID) {
}