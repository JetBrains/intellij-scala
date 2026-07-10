package org.jetbrains.sbt.process.mock

import com.intellij.execution.configurations.JavaParameters
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Key

import java.nio.file.Path

/**
 * @see [[org.jetbrains.sbt.process.mock.MockSbtProcessForTestsSetup]]
 */
private[sbt] object MockSbtProcessForTests {
  private[sbt] val MockProcessDataProjectStateKey: Key[MockProcessData] =
    Key.create("org.jetbrains.sbt.mock.process.state")

  private def state(project: Project): Option[MockProcessData] =
    Option(project.getUserData(MockProcessDataProjectStateKey))

  // Keep these in sync with values in sbt-mock-process/src/MockSbtProcess.java.
  private object VmOptions {
    val ModeProperty = "org.jetbrains.sbt.mock.process.mode"

    val NoShellMode = "no-shell"
    val NoShellStdinMode = "no-shell-stdin"
    val OldShellMode = "old-shell"
    val NewShellMode = "new-shell"
    val SlowShutdownReleaseFileProperty = "org.jetbrains.sbt.mock.process.slowShutdownReleaseFile"
    val SlowShutdownStartedFileProperty = "org.jetbrains.sbt.mock.process.slowShutdownStartedFile"
  }

  final class MockProcessData private[sbt](
    private val classpath: Path,
    private val mainClass: String,
    private val slowShutdownReleaseFile: Option[Path] = None,
    private val slowShutdownStartedFile: Option[Path] = None,
  ) {
    def configureJavaParameters(params: JavaParameters): Unit = {
      params.getClassPath.clear()
      params.getClassPath.add(classpath.toString)
      params.setMainClass(mainClass)
      slowShutdownVmOptions.foreach(params.getVMParametersList.add)
    }

    def mainClassCommandLineTail: Seq[String] =
      slowShutdownVmOptions ++ Seq("-cp", classpath.toString, mainClass)

    private def slowShutdownVmOptions: Seq[String] =
      slowShutdownReleaseFile.toSeq.map(path => s"-D${VmOptions.SlowShutdownReleaseFileProperty}=$path") ++
        slowShutdownStartedFile.toSeq.map(path => s"-D${VmOptions.SlowShutdownStartedFileProperty}=$path")
  }

  def isEnabled(project: Project): Boolean =
    state(project).isDefined

  def configureJavaParametersForNonSbtShell(project: Project, params: JavaParameters): Unit = {
    val mockState = state(project).getOrElse(return)
    mockState.configureJavaParameters(params)
    params.getVMParametersList.add(mockModeVmOption(VmOptions.NoShellMode))
  }

  def mockMainClassCommandLineTailForNonShellFromStdin(project: Project): Seq[String] = {
    val mockState = state(project).getOrElse(return Seq.empty)
    mockModeVmOption(VmOptions.NoShellStdinMode) +: mockState.mainClassCommandLineTail
  }

  def mockMainClassCommandLineTailForSbtShell(project: Project, useNewShell: Boolean): Seq[String] = {
    val mockState = state(project).getOrElse(return Seq.empty)
    val mode = if (useNewShell) VmOptions.NewShellMode else VmOptions.OldShellMode
    val modeOption = mockModeVmOption(mode)
    modeOption +: mockState.mainClassCommandLineTail
  }

  private def mockModeVmOption(mode: String): String =
    s"-D${VmOptions.ModeProperty}=$mode"
}
