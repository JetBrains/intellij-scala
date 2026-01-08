package org.jetbrains.bsp.project.importing.preimport

import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.projectRoots.{JavaSdk, ProjectJdkTable, Sdk}
import com.intellij.openapi.util.io.FileUtil
import org.jetbrains.bsp.BspBundle
import org.jetbrains.bsp.buildinfo.BuildInfo
import org.jetbrains.plugins.scala.build.{BuildMessages, BuildReporter}
import org.jetbrains.plugins.scala.extensions.invokeAndWait
import org.jetbrains.sbt.SbtUtil.{detectSbtVersion, sbtVersionParam}
import org.jetbrains.sbt.process.SbtRunner
import org.jetbrains.sbt.project.SbtExternalSystemManager
import org.jetbrains.sbt.{Sbt, SbtUtil, SbtVersion}

import java.nio.file.Path
import scala.util.Try

class BloopPreImporter(runSbt: (SbtRunner, ProgressIndicator) => Try[BuildMessages]) extends PreImporter {
  private val runner = new SbtRunner()
  override def cancel(): Unit = runner.cancel()
  def run(indicator: ProgressIndicator): Try[BuildMessages] = runSbt(runner, indicator)
}
object BloopPreImporter {
  def apply(baseDir: Path, jdk: Sdk)(implicit reporter: BuildReporter): BloopPreImporter = {
    invokeAndWait(ProjectJdkTable.getInstance.preconfigure())
    val jdkType = JavaSdk.getInstance()
    val jdkExe = Path.of(jdkType.getVMExecutablePath(jdk))
    val jdkHome = Option(jdk.getHomePath).map(Path.of(_))
    val sbtLauncher = SbtUtil.defaultLauncherPath

    val injectedPlugins = s"""addSbtPlugin("ch.epfl.scala" % "sbt-bloop" % "${BuildInfo.bloopVersion}")"""
    val pluginFile = FileUtil.createTempFile("idea",Sbt.Extension, true)
    val pluginFilePath = SbtUtil.normalizePath(pluginFile.toPath)
    FileUtil.writeToFile(pluginFile, injectedPlugins)

    val injectedSettings = """bloopExportJarClassifiers in Global := Some(Set("sources"))"""
    val settingsFile = FileUtil.createTempFile(baseDir.toFile, "idea-bloop", Sbt.Extension, true)
    FileUtil.writeToFile(settingsFile, injectedSettings)

    val sbtLauncherArgs = List(
      "early(addPluginSbtFile=\"\"\"" + pluginFilePath + "\"\"\")"
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
        indicator, baseDir, jdkExe, vmArgs,
        Map.empty, sbtLauncher, Seq.empty, sbtLauncherArgs, sbtCommands,
        BspBundle.message("bsp.resolver.creating.bloop.configuration.from.sbt"), passParentEnvironment = true, timingCollector = None
      )
      new BloopPreImporter(runDump)
    } finally {
      settingsFile.delete()
    }
  }
}
