package org.jetbrains.bsp.project.importing.preimport

import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.projectRoots.{JavaSdk, ProjectJdkTable, Sdk}
import com.intellij.openapi.util.io.NioFiles
import com.intellij.platform.eel.EelDescriptor
import org.jetbrains.bsp.BspBundle
import org.jetbrains.bsp.buildinfo.BuildInfo
import org.jetbrains.plugins.scala.build.{BuildMessages, BuildReporter}
import org.jetbrains.plugins.scala.extensions.invokeAndWait
import org.jetbrains.sbt.SbtUtil.{detectSbtVersion, sbtVersionParam}
import org.jetbrains.sbt.process.SbtRunner
import org.jetbrains.sbt.project.settings.SbtExecutionSettings
import org.jetbrains.sbt.project.{EelPathKotlinUtils, SbtExternalSystemManager}
import org.jetbrains.sbt.{Sbt, SbtUtil, SbtVersion, normalizedLocalPath}

import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Path}
import scala.util.Try

class BloopPreImporter(runSbt: (SbtRunner, ProgressIndicator) => Try[BuildMessages]) extends PreImporter {
  private val runner = new SbtRunner()
  override def cancel(): Unit = runner.cancel()
  def run(indicator: ProgressIndicator): Try[BuildMessages] = runSbt(runner, indicator)
}
object BloopPreImporter {
  //noinspection ApiStatus
  def apply(baseDir: Path, jdk: Sdk)(using reporter: BuildReporter, eelDescriptor: EelDescriptor): BloopPreImporter = {
    invokeAndWait(ProjectJdkTable.getInstance.preconfigure())
    val jdkType = JavaSdk.getInstance()
    val jdkExe = Path.of(jdkType.getVMExecutablePath(jdk))
    val jdkHome = Option(jdk.getHomePath).map(Path.of(_))
    val sbtLauncher = SbtUtil.defaultLauncherPath

    val injectedPlugins = s"""addSbtPlugin("ch.epfl.scala" % "sbt-bloop" % "${BuildInfo.bloopVersion}")"""
    val pluginFile = SbtUtil.createTemporarySbtFile(injectedPlugins, eelDescriptor, projectOpt = None)
    Runtime.getRuntime.addShutdownHook(Thread(() => NioFiles.deleteQuietly(pluginFile)))

    val injectedSettings = """bloopExportJarClassifiers in Global := Some(Set("sources"))"""
    val settingsFile = EelPathKotlinUtils.createTemporaryFile("idea-bloop", Sbt.Extension, baseDir, eelDescriptor)
    Files.writeString(settingsFile, injectedSettings, StandardCharsets.UTF_8)

    val sbtLauncherArgs = List(
      "early(addPluginSbtFile=\"\"\"" + pluginFile.normalizedLocalPath + "\"\"\")"
    )
    val sbtCommands = "bloopInstall"

    val projectSbtVersion = detectSbtVersion(baseDir, SbtUtil.defaultLauncherPath)
    val sbtVersion = SbtVersion.upgradeSbtVersionToTheLatestCompatible(projectSbtVersion)
    val upgradeParam =
      if (sbtVersion > projectSbtVersion)
        List(sbtVersionParam(sbtVersion))
      else List.empty

    val vmArgs = SbtExternalSystemManager.getVmOptions(Seq.empty, jdkHome) ++ upgradeParam

    try {
      val runDump = (runner: SbtRunner, indicator: ProgressIndicator) => runner.runSbt(
        indicator = indicator,
        directory = baseDir,
        vmExecutable = jdkExe,
        vmOptions = vmArgs,
        environment0 = Map.empty,
        sbtLauncher = sbtLauncher,
        sbtOptions = SbtExecutionSettings.SbtOptions.empty,
        sbtLauncherArgs = sbtLauncherArgs,
        sbtCommands = sbtCommands,
        reportMessage = BspBundle.message("bsp.resolver.creating.bloop.configuration.from.sbt"),
        passParentEnvironment = true
      )
      new BloopPreImporter(runDump)
    } finally {
      NioFiles.deleteQuietly(settingsFile)
    }
  }
}
