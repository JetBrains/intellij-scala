package org.jetbrains.bsp.project.test.environment

import java.lang

import com.intellij.execution.BeforeRunTaskProvider
import com.intellij.execution.configurations.ModuleBasedConfiguration
import com.intellij.execution.configurations.RunConfiguration
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.module.Module
import com.intellij.openapi.util.Key
import javax.swing.Icon
import org.jetbrains.bsp.BspBundle
import org.jetbrains.bsp.BspUtil
import org.jetbrains.bsp.Icons
import org.jetbrains.bsp.protocol.BspCommunication
import org.jetbrains.concurrency.Promise
import org.jetbrains.concurrency.Promises

class BspFetchEnvironmentTaskProvider extends BeforeRunTaskProvider[BspFetchEnvironmentTask] {
  private val logger = Logger.getInstance(classOf[BspCommunication])

  override def getId: Key[BspFetchEnvironmentTask] = BspFetchEnvironmentTask.runTaskKey

  override def getName: String = BspBundle.message("bsp.task.name")

  override def getIcon: Icon = Icons.BSP

  override def createTask(runConfiguration: RunConfiguration): BspFetchEnvironmentTask = {
    runConfiguration match {
      case BspSupportedConfiguration(_, _) => new BspFetchEnvironmentTask
      case _ => null
    }
  }

  override def isConfigurable: Boolean = true

  override def configureTask(context: DataContext, configuration: RunConfiguration, task: BspFetchEnvironmentTask): Promise[lang.Boolean] = {
    configuration match {
      case BspSupportedConfiguration(_, module) =>
        val potentialTargets = BspJvmEnvironment.getBspTargets(module) match {
          case Right(targets) => targets
          case Left(error) =>
            logger.error(BspBundle.message("bsp.task.could.not.get.potential.targets", error.msg))
            Seq.empty
        }
        BspJvmEnvironment.promptUserToSelectBspTarget(configuration.getProject, potentialTargets, task)
        Promises.resolvedPromise(potentialTargets.length == 1)
      case _ => Promises.resolvedPromise(false)
    }
  }

  override def executeTask(
    context: DataContext,
    configuration: RunConfiguration,
    env: ExecutionEnvironment,
    task: BspFetchEnvironmentTask
  ): Boolean = {
    try {
      configuration match {
        case BspSupportedConfiguration(config, module) =>
          BspJvmEnvironment.resolveForRun(config, module, task) match {
            case Right(environment) =>
              config.putUserData(BspFetchEnvironmentTask.jvmEnvironmentKey, environment)
            case Left(error) =>
              logger.error(BspBundle.message("bsp.task.error", error.msg))
          }
        case _ => ()
      }
    } catch {
      case error: Throwable => logger.error(error)
    }
    true
  }


  private object BspSupportedConfiguration {
    def unapply(configuration: RunConfiguration): Option[(ModuleBasedConfiguration[_, _], Module)] = {
      org.jetbrains.bsp.project.test.environment.ModuleBasedConfiguration.unapply(configuration)
        .filter(_ => BspEnvironmentRunnerExtension.isSupported(configuration))
    }
  }

}
