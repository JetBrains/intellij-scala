package org.jetbrains.bsp.project.importing.setup

import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.projectRoots.{JavaSdk, ProjectJdkTable, Sdk}
import org.jetbrains.bsp.BspBundle
import org.jetbrains.plugins.scala.build.{BuildMessages, BuildReporter}
import org.jetbrains.plugins.scala.extensions.invokeAndWait
import org.jetbrains.sbt.SbtUtil.{detectSbtVersion, getDefaultLauncher, sbtVersionParam}
import org.jetbrains.sbt.project.SbtExternalSystemManager
import org.jetbrains.sbt.project.structure.SbtStructureDump
import org.jetbrains.sbt.{SbtUtil, SbtVersion}

import java.nio.file.Path
import scala.util.Try

class SbtConfigSetup(dumper: SbtStructureDump, runInit: (ProgressIndicator, BuildReporter) => Try[BuildMessages]) extends BspConfigSetup {

  override def cancel(): Unit = dumper.cancel()
  override def run(indicator: ProgressIndicator)(implicit reporter: BuildReporter): Try[BuildMessages] =
    runInit(indicator, reporter)
}

object SbtConfigSetup {

  /** Runs sbt with a dummy command so that the project is initialized and .bsp/sbt.json is created. */
  def apply(baseDir: Path, jdk: Sdk): SbtConfigSetup = {
    invokeAndWait {
      ProjectJdkTable.getInstance.preconfigure()
    }
    val jdkType = JavaSdk.getInstance()
    val jdkExe = Path.of(jdkType.getVMExecutablePath(jdk))
    val jdkHome = Option(jdk.getHomePath).map(Path.of(_))
    val sbtLauncher = SbtUtil.getDefaultLauncher

    // dummy command so that sbt will run, init and exit
    val sbtLauncherArgs = List("early(startServer)")
    val sbtCommands = ""

    val projectSbtVersion = detectSbtVersion(baseDir, getDefaultLauncher.toPath)
    val sbtVersion = SbtVersion.upgradeSbtVersionToTheLatestCompatible(projectSbtVersion)
    val upgradeParam =
      if (sbtVersion > projectSbtVersion)
        List(sbtVersionParam(sbtVersion))
      else List.empty

    val vmArgs = SbtExternalSystemManager.getVmOptions(Seq.empty, jdkHome.map(_.toFile)) ++ upgradeParam

    val dumper = new SbtStructureDump()
    val runInit = (indicator: ProgressIndicator, reporter: BuildReporter) => dumper.runSbt(
      indicator, baseDir.toFile, jdkExe.toFile, vmArgs,
      Map.empty, sbtLauncher, Seq.empty, sbtLauncherArgs, sbtCommands,
      BspBundle.message("bsp.resolver.creating.sbt.configuration"), passParentEnvironment = true
    )(reporter)
    new SbtConfigSetup(dumper, runInit)
  }
}
