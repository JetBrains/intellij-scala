package org.jetbrains.bsp.project.importing.setup

import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.projectRoots.{JavaSdk, ProjectJdkTable, Sdk}
import org.jetbrains.annotations.TestOnly
import org.jetbrains.bsp.BspBundle
import org.jetbrains.plugins.scala.build.{BuildMessages, BuildReporter}
import org.jetbrains.plugins.scala.extensions.invokeAndWait
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

  /** Runs sbt with a dummy command so that the project is initialized and .bsp/sbt.json is created.
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

    // dummy command so that sbt will run, init and exit
    val sbtLauncherArgs = List("early(startServer)")
    val sbtCommands = ""

    val projectSbtVersion = detectSbtVersion(baseDir, defaultLauncherPath)
    val sbtVersion = SbtVersion.upgradeSbtVersionToTheLatestCompatible(projectSbtVersion)
    val upgradeParam =
      if (sbtVersion > projectSbtVersion)
        List(sbtVersionParam(sbtVersion))
      else List.empty

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
      sbtCommands,
      BspBundle.message("bsp.resolver.creating.sbt.configuration"),
      passParentEnvironment = true,
      timingCollector = None
    )(using reporter)
    new SbtConfigSetup(runInit)
  }
}
