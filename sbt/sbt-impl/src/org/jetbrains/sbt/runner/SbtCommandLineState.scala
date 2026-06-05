package org.jetbrains.sbt.runner

import com.intellij.execution.configurations.*
import com.intellij.execution.process.ProcessEvent
import com.intellij.execution.runners.{ExecutionEnvironment, ProgramRunner}
import com.intellij.execution.util.EnvFilesUtilKt.configureEnvsFromFiles
import com.intellij.execution.util.JavaParametersUtil
import com.intellij.execution.{ExecutionResult, Executor, OutputListener}
import com.intellij.openapi.externalSystem.service.execution.ExternalSystemJdkUtil
import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.util.Key
import org.jetbrains.plugins.scala.extensions.PathExt
import org.jetbrains.plugins.scala.util.JarManifestUtils
import org.jetbrains.sbt.SbtUtil
import org.jetbrains.sbt.process.SbtProcessOutputDiagnosticsCollector
import org.jetbrains.sbt.process.mock.MockSbtProcessForTests
import org.jetbrains.sbt.project.settings.SbtExecutionSettings
import org.jetbrains.sbt.project.{<<, SbtExternalSystemManager}
import org.jetbrains.sbt.settings.SbtSettings

import java.nio.file.Path
import java.util

/**
 * Execution state for an [[SbtRunConfiguration]].
 *
 * A state instance is created for every SBT run configuration, regardless of whether the configuration is executed in
 * an existing sbt shell or as a separate JVM process (with a warning, see below)
 *
 * In separate-process mode this class acts as a regular [[JavaCommandLineState]]: the IntelliJ platform calls
 * [[createJavaParameters]], and the returned [[JavaParameters]] describe the sbt launcher JVM process to start.
 * That path configures the working directory, environment, JDK, sbt launcher classpath/main class, VM parameters, and
 * program parameters containing the sbt commands.
 *
 * In sbt-shell mode the custom SBT program runners ([[SbtProgramRunner]] and [[SbtDebugProgramRunner]])
 * intercept this state and submit [[processedCommands]] to the already-running shell instead.
 * In that mode [[createJavaParameters]] is not part of the process launch path;
 * the state is used as a holder for the commands, configuration, and optional output listener.
 *
 * @note Background IDE sbt tasks such as project import use [[org.jetbrains.sbt.process.SbtRunner]] instead.
 * That runner owns the process lifecycle directly via `GeneralCommandLine`,
 * while this class adapts run configurations to the IntelliJ run/debug execution infrastructure.
 */
private final class SbtCommandLineState(
  val processedCommands: String,
  val configuration: SbtRunConfiguration,
  environment: ExecutionEnvironment,
  private var listener: Option[String => Unit] = None
) extends JavaCommandLineState(environment) {

  def getListener: Option[String => Unit] = listener

  override def execute(executor: Executor, runner: ProgramRunner[?]): ExecutionResult = {
    val result = super.execute(executor, runner)
    Option(result.getProcessHandler).foreach { processHandler =>
      SbtProcessOutputDiagnosticsCollector.collectProcessOutputFrom(
        processHandler,
        processTitle = s"SBT run configuration process output (${configuration.getName})",
      )
    }

    listener.foreach(_ => {
      val outputListener = new OutputListener() {
        override def onTextAvailable(event: ProcessEvent, outputType: Key[?]): Unit =
          super.onTextAvailable(event, outputType)
      }
      val processHandler = Option(result.getProcessHandler)
      processHandler.foreach(_.addProcessListener(outputListener))
    })

    result
  }

  override def createJavaParameters(): JavaParameters = {
    val params = createJavaParametersImpl
    MockSbtProcessForTests.configureJavaParametersForNonSbtShell(configuration.getProject, params)
    params
  }

  private def createJavaParametersImpl: JavaParameters = {
    val project = configuration.getProject
    val params: JavaParameters = new JavaParameters

    params.setWorkingDirectory(configuration.workingDir)

    val sbtExecutionSettings = SbtExternalSystemManager.executionSettingsFor(project)

    val jdk = getJdk(project, sbtExecutionSettings)
    params.configureByProject(project, JavaParameters.JDK_ONLY, jdk)

    val environmentVariables = new util.HashMap(configuration.environmentVariables)
    environmentVariables.putAll(configureEnvsFromFiles(configuration, true))
    params.setEnv(environmentVariables)

    val sbtSystemSettings = SbtSettings.getInstance(project).getState

    setClasspathAndMainClass(params, sbtSystemSettings)

    // NOTE: This path passes environment variables to the process, but it does not resolve JAVA_OPTS/.jvmopts
    // or SBT_OPTS/.sbtopts into JVM or sbt launcher arguments.
    // SbtRunner and sbt shell do that through SbtProcessOptionsResolver.
    // TODO: Share the resolver-backed launch model here before adding VM and program parameters.
    params.getVMParametersList.addParametersString(configuration.vmparams)
    params.getProgramParametersList.addParametersString(processedCommands)

    params
  }

  private def setClasspathAndMainClass(params: JavaParameters, sbtSystemSettings: SbtSettings.State): Unit = {
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
  }

  private def determineMainClass(launcherPath: String): String = {
    val jar = Path.of(launcherPath)
    JarManifestUtils.readManifestAttribute(jar, "Main-Class").getOrElse("xsbt.boot.Boot")
  }

  private def getJdk(project: Project, sbtExecutionSettings: SbtExecutionSettings): Sdk = {
    val customJdk: Option[Sdk] = for {
      vmExecutablePath <- sbtExecutionSettings.getCustomVMExecutableOrWarn(project)
      // The java installation directory is two levels up.
      // See org.jetbrains.sbt.project.SbtExternalSystemManager.getVmExecutable
      javaHome = vmExecutablePath << 2
      if javaHome != null
      jdk <- Option(ExternalSystemJdkUtil.findJdkInSdkTableByPath(javaHome.toCanonicalPath.toString))
    } yield jdk

    customJdk.getOrElse {
      JavaParametersUtil.createProjectJdk(project, null)
    }
  }
}
