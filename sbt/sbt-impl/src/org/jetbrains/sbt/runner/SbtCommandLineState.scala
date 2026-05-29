package org.jetbrains.sbt.runner

import com.intellij.execution.configurations.*
import com.intellij.execution.process.ProcessEvent
import com.intellij.execution.runners.{ExecutionEnvironment, ProgramRunner}
import com.intellij.execution.util.EnvFilesUtilKt.configureEnvsFromFiles
import com.intellij.execution.util.JavaParametersUtil
import com.intellij.execution.{ExecutionResult, Executor, OutputListener}
import com.intellij.openapi.externalSystem.service.execution.ExternalSystemJdkUtil
import com.intellij.openapi.util.Key
import org.jetbrains.plugins.scala.extensions.PathExt
import org.jetbrains.plugins.scala.util.JarManifestUtils
import org.jetbrains.sbt.SbtUtil
import org.jetbrains.sbt.process.SbtProcessOutputDiagnosticsCollector
import org.jetbrains.sbt.project.{<<, SbtExternalSystemManager}
import org.jetbrains.sbt.settings.SbtSettings

import java.nio.file.Path
import java.util

class SbtCommandLineState(
  val processedCommands: String,
  val configuration: SbtRunConfiguration,
  environment: ExecutionEnvironment,
  private var listener: Option[String => Unit] = None
) extends JavaCommandLineState(environment) {

  def getListener: Option[String => Unit] = listener

  override def execute(executor: Executor, runner: ProgramRunner[?]): ExecutionResult = {
    val r = super.execute(executor, runner)
    Option(r.getProcessHandler).foreach { processHandler =>
      SbtProcessOutputDiagnosticsCollector.collectProcessOutputFrom(
        processHandler,
        processTitle = s"SBT run configuration process output (${configuration.getName})",
      )
    }
    listener.foreach(_ => Option(r.getProcessHandler).foreach(_.addProcessListener(new OutputListener() {
      override def onTextAvailable(event: ProcessEvent, outputType: Key[?]): Unit = super.onTextAvailable(event, outputType)
    })))
    r
  }

  def determineMainClass(launcherPath: String): String = {
    val jar = Path.of(launcherPath)
    JarManifestUtils.readManifestAttribute(jar, "Main-Class").getOrElse("xsbt.boot.Boot")
  }

  override def createJavaParameters(): JavaParameters = {
    val project = configuration.getProject
    val params: JavaParameters = new JavaParameters

    params.setWorkingDirectory(configuration.workingDir)

    val sbtExecutionSettings = SbtExternalSystemManager.executionSettingsFor(project)

    val customJdk = for {
      vmExecutablePath <- sbtExecutionSettings.getCustomVMExecutableOrWarn(project)
      // The java installation directory is two levels up.
      // See org.jetbrains.sbt.project.SbtExternalSystemManager.getVmExecutable
      javaHome = vmExecutablePath << 2
      if javaHome != null
      jdk <- Option(ExternalSystemJdkUtil.findJdkInSdkTableByPath(javaHome.toCanonicalPath.toString))
    } yield jdk

    val jdk = customJdk.getOrElse(JavaParametersUtil.createProjectJdk(project, null))
    params.configureByProject(project, JavaParameters.JDK_ONLY, jdk)

    val environmentVariables = new util.HashMap(configuration.environmentVariables)
    environmentVariables.putAll(configureEnvsFromFiles(configuration, true))
    params.setEnv(environmentVariables)

    val sbtSystemSettings = SbtSettings.getInstance(project).getState

    // One of these checks might be redundant.
    // Why do we need the customLauncherEnabled at all?
    if (sbtSystemSettings.customLauncherPath != null) {
      params.getClassPath.add(sbtSystemSettings.customLauncherPath)
      params.setMainClass(determineMainClass(sbtSystemSettings.customLauncherPath))
    } else {
      val launcher = SbtUtil.defaultLauncherPath
      val launcherPath = launcher.toCanonicalPath.toString
      params.getClassPath.add(launcherPath)
      params.setMainClass(determineMainClass(launcherPath))
    }

    params.getVMParametersList.addParametersString(configuration.vmparams)
    params.getProgramParametersList.addParametersString(processedCommands)

    params
  }
}
