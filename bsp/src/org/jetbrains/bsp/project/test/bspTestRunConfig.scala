package org.jetbrains.bsp.project.test

import java.io.OutputStream
import java.net.URI
import java.util.UUID
import java.util.concurrent.CompletableFuture

import ch.epfl.scala.bsp4j
import ch.epfl.scala.bsp4j.TestResult
import com.intellij.execution.configurations._
import com.intellij.execution.impl.ConsoleViewImpl
import com.intellij.execution.process.{ProcessHandler, ProcessOutputType}
import com.intellij.execution.runners.{ExecutionEnvironment, ProgramRunner}
import com.intellij.execution.{DefaultExecutionResult, ExecutionResult, Executor}
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.options.SettingsEditor
import com.intellij.openapi.project.Project
import javax.swing.{Icon, JComponent}
import org.jetbrains.bsp.Icons
import org.jetbrains.bsp.data.BspMetadata
import org.jetbrains.bsp.protocol.BspCommunicationComponent
import org.jetbrains.bsp.protocol.BspNotifications.BspNotification
import org.jetbrains.bsp.protocol.session.BspSession.BspServer

import scala.collection.JavaConverters._
import scala.concurrent.ExecutionContext.Implicits._


class BspTestRunType extends ConfigurationType {
  override def getDisplayName: String = "BSP test run"

  override def getConfigurationTypeDescription: String = getDisplayName

  override def getIcon: Icon = Icons.BSP

  override def getId: String = "BSP_TEST_RUN_CONFIGURATION"

  override def getConfigurationFactories: Array[ConfigurationFactory] = Array(new BspTestRunFactory(this))
}

class BspTestRunFactory(t: ConfigurationType) extends ConfigurationFactory(t) {
  override def createTemplateConfiguration(project: Project): RunConfiguration = new BspTestRunConfiguration(project, this, "BSP_TEST_RUN")

  override def getName: String = "BspTestRunFactory"
}


class BspTestRunConfiguration(val project: Project, val configurationFactory: ConfigurationFactory, val name: String)
  extends RunConfigurationBase[String](project, configurationFactory, name) {
  override def getConfigurationEditor: SettingsEditor[_ <: RunConfiguration] = new SettingsEditor[RunConfiguration]() {
    override def resetEditorFrom(s: RunConfiguration): Unit = {}

    override def applyEditorTo(s: RunConfiguration): Unit = {}

    override def createEditor(): JComponent = new JComponent {}
  }

  private def targets(): List[URI] = {
    val proj = getProject
    ModuleManager.getInstance(proj).getModules.toList
      .flatMap(BspMetadata.get(proj, _))
      .flatMap(x => x.targetIds.asScala.toList)
  }

  private def testRequest(server: BspServer): CompletableFuture[TestResult] = {
    val targetIds = targets().map(uri => new bsp4j.BuildTargetIdentifier(uri.toString))
    val params = new bsp4j.TestParams(targetIds.toList.asJava)
    params.setOriginId(UUID.randomUUID().toString)
    server.buildTargetTest(params)
  }

  class MProcHandler extends ProcessHandler {
    override def destroyProcessImpl(): Unit = ()

    override def detachProcessImpl(): Unit = {}

    override def detachIsDefault(): Boolean = false

    override def getProcessInput: OutputStream = null

    def shutdown(): Unit = {
      super.notifyProcessTerminated(0)
    }
  }

  override def getState(executor: Executor, environment: ExecutionEnvironment): RunProfileState = new RunProfileState {
    override def execute(executor: Executor, runner: ProgramRunner[_ <: RunnerSettings]): ExecutionResult = {
      val console = new ConsoleViewImpl(getProject, true)
      val procHandler = new MProcHandler
      console.attachToProcess(procHandler)
      val bspCommunication = project.getComponent(classOf[BspCommunicationComponent]).communication

      def notification(n: BspNotification): Unit = {
        procHandler.notifyTextAvailable(n.toString, ProcessOutputType.STDOUT)
      }

      bspCommunication.run(testRequest, notification, procHandler.notifyTextAvailable(_, ProcessOutputType.STDOUT))
        .future
        .onComplete(_ => procHandler.shutdown())

      new DefaultExecutionResult(console, procHandler)
    }
  }
}