package org.jetbrains.bsp.project.importing.setup

import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.projectRoots.{JavaSdk, ProjectJdkTable, Sdk}
import org.jetbrains.annotations.TestOnly
import org.jetbrains.bsp.BspBundle
import org.jetbrains.plugins.scala.build.{BuildMessages, BuildReporter}
import org.jetbrains.plugins.scala.extensions.invokeAndWait
import org.jetbrains.plugins.scala.isUnitTestMode
import org.jetbrains.sbt.SbtUtil.{defaultLauncherPath, detectSbtVersion, sbtVersionParam}
import org.jetbrains.sbt.SbtVersion
import org.jetbrains.sbt.process.SbtRunner
import org.jetbrains.sbt.project.SbtExternalSystemManager
import org.jetbrains.sbt.project.settings.SbtExecutionSettings

import java.nio.file.Path
import scala.util.Try

class SbtConfigSetup(runInit: (SbtRunner, ProgressIndicator, BuildReporter) => Try[BuildMessages])
  extends BspConfigSetup {
  private val runner = new SbtRunner()

  override def cancel(): Unit = runner.cancel()
  override def run(indicator: ProgressIndicator)(implicit reporter: BuildReporter): Try[BuildMessages] =
    runInit(runner, indicator, reporter)
}

object SbtConfigSetup {

  def apply(baseDir: Path, jdk: Sdk): SbtConfigSetup =
    createSbtConfigSetup(baseDir, jdk, environment = Map.empty)

  @TestOnly
  def apply(baseDir: Path, jdk: Sdk, environment: Map[String, String]): SbtConfigSetup =
    createSbtConfigSetup(baseDir, jdk, environment)

  /** Runs sbt so that the project is initialized and .bsp/sbt.json is created.
   *
   * @param baseDir     the project base directory
   * @param jdk         the JDK to use for running sbt
   * @param environment additional environment variables to pass to sbt process (e.g., JAVA_HOME for tests)
   */
  private def createSbtConfigSetup(baseDir: Path, jdk: Sdk, environment: Map[String, String]): SbtConfigSetup = {
    invokeAndWait {
      ProjectJdkTable.getInstance.preconfigure()
    }
    val jdkType = JavaSdk.getInstance()
    val jdkExe = Path.of(jdkType.getVMExecutablePath(jdk))
    val jdkHome = Option(jdk.getHomePath).map(Path.of(_))
    val sbtLauncher = defaultLauncherPath

    val projectSbtVersion = detectSbtVersion(baseDir, defaultLauncherPath)
    val sbtVersion = SbtVersion.upgradeSbtVersionToTheLatestCompatible(projectSbtVersion)
    val upgradeParam =
      if (sbtVersion > projectSbtVersion)
        List(sbtVersionParam(sbtVersion))
      else List.empty

    val sbtLauncherArgs = getCommandForConnectionFileGeneration(sbtVersion)

    val vmArgs = SbtExternalSystemManager.getVmOptions(Seq.empty, jdkHome) ++ upgradeParam

    val runInit = (runner: SbtRunner, indicator: ProgressIndicator, reporter: BuildReporter) => runner.runSbt(
      indicator,
      baseDir,
      jdkExe,
      vmArgs,
      environment,
      sbtLauncher,
      SbtExecutionSettings.SbtOptions.empty,
      sbtLauncherArgs,
      sbtCommands = "",
      BspBundle.message("bsp.resolver.creating.sbt.configuration"),
      passParentEnvironment = true,
      timingCollector = None
    )(using reporter)
    new SbtConfigSetup(runInit)
  }

  /**
   * The sbt launcher arguments used to generate the `.bsp/sbt.json` connection file.
   *
   * In sbt 2.x projects and in tests a `bspConfig` task is used instead of the `startServer` command.
   * `startServer` starts a non-daemon thread. In principle, sbt should stop this thread when it exits,
   * but on CI we have observed that generating the connection file sometimes hangs. Starting a server thread
   * is generally not needed to generate the BSP connection file, and when a dedicated sbt task exists for this purpose,
   * it should be used.
   */
  private def getCommandForConnectionFileGeneration(sbtVersion: SbtVersion): List[String] = {
    val useBspConfig = isUnitTestMode || sbtVersion.isSbt2
    if useBspConfig then
      List("bspConfig")
    else
      List("early(startServer)")
  }
}
