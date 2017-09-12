package org.jetbrains.plugins.cbt.runner

import com.intellij.execution.configurations.RunConfiguration
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.execution.{BeforeRunTask, BeforeRunTaskProvider, ExecutionManager}
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.module.Module
import com.intellij.openapi.util.Key
import com.intellij.util.concurrency.Semaphore

class RunCbtDebuggerBeforeRunProvider extends BeforeRunTaskProvider[RunCbtDebuggerBeforeRunTask] {
  override def getId: Key[RunCbtDebuggerBeforeRunTask] = RunCbtDebuggerBeforeRunProvider.ID

  override def getName: String = RunCbtDebuggerBeforeRunProvider.NAME

  override def isConfigurable: Boolean = false

  override def getDescription(task: RunCbtDebuggerBeforeRunTask): String =
    RunCbtDebuggerBeforeRunProvider.NAME

  override def canExecuteTask(configuration: RunConfiguration,
                              task: RunCbtDebuggerBeforeRunTask): Boolean =
    task.isInstanceOf[RunCbtDebuggerBeforeRunTask]

  override def configureTask(runConfiguration: RunConfiguration,
                             task: RunCbtDebuggerBeforeRunTask): Boolean = false

  override def createTask(runConfiguration: RunConfiguration): RunCbtDebuggerBeforeRunTask =
    null

  override def executeTask(ctx: DataContext,
                           c: RunConfiguration,
                           env: ExecutionEnvironment,
                           beforeTunTask: RunCbtDebuggerBeforeRunTask): Boolean = {
    val project = env.getProject
    val finished = new Semaphore
    var result: Boolean = false
    finished.down()
    val listener = new CbtProcessListener {
      override def onComplete(): Unit = ()
      override def onTextAvailable(text: String, stderr: Boolean): Unit = {
        if (text startsWith "Listening for transport") {
          result = true
          Thread.sleep(500)
          finished.up()
        }
        if (text startsWith "ERROR:") {
          result = false
          Thread.sleep(500)
          finished.up()
        }
      }
    }

    val environment =
      CbtProjectTaskRunner.createExecutionEnv(beforeTunTask.taskName,
        beforeTunTask.module,
        project,
        listener,
        options = Seq("-debug"))
    ExecutionManager.getInstance(project)
      .restartRunProfile(environment)
    finished.waitFor()
    result
  }

}

object RunCbtDebuggerBeforeRunProvider {
  lazy val ID: Key[RunCbtDebuggerBeforeRunTask] = Key.create(NAME)
  val NAME = "CBT Debug"
}

class RunCbtDebuggerBeforeRunTask(val taskName: String, val module: Module)
  extends BeforeRunTask[RunCbtDebuggerBeforeRunTask](RunCbtDebuggerBeforeRunProvider.ID) {
  setEnabled(true)
}
