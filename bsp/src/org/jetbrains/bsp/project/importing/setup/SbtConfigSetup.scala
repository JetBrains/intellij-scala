package org.jetbrains.bsp.project.importing.setup
import com.intellij.openapi.projectRoots.{JavaSdk, ProjectJdkTable, Sdk}
import org.jetbrains.bsp.BspBundle
import org.jetbrains.plugins.scala.build.{BuildMessages, BuildReporter}
import org.jetbrains.plugins.scala.extensions.invokeAndWait
import org.jetbrains.plugins.scala.project.Version
import org.jetbrains.sbt.SbtUtil
import org.jetbrains.sbt.SbtUtil.{detectSbtVersion, getDefaultLauncher, sbtVersionParam, upgradedSbtVersion}
import org.jetbrains.sbt.project.SbtExternalSystemManager
import org.jetbrains.sbt.project.structure.SbtStructureDump

import java.io.File
import scala.util.Try

class SbtConfigSetup(dumper: SbtStructureDump, runInit: BuildReporter => Try[BuildMessages]) extends BspConfigSetup {

  override def cancel(): Unit = dumper.cancel()
  override def run(implicit reporter: BuildReporter): Try[BuildMessages] =
    runInit(reporter)
}

object SbtConfigSetup {

  /** Runs sbt with a dummy command so that the project is initialized and .bsp/sbt.json is created. */
  def apply(baseDir: File): SbtConfigSetup = {
    invokeAndWait {
      ProjectJdkTable.getInstance.preconfigure()
    }
    val jdkType = JavaSdk.getInstance()
    val jdk = ProjectJdkTable.getInstance().findMostRecentSdkOfType(jdkType)
    apply(baseDir, jdk)
  }

  def apply(baseDir: File, jdk: Sdk): SbtConfigSetup = {
    val jdkType = JavaSdk.getInstance()
    val jdkExe = new File(jdkType.getVMExecutablePath(jdk))
    val jdkHome = Option(jdk.getHomePath).map(new File(_))
    val sbtLauncher = SbtUtil.getDefaultLauncher

    // dummy command so that sbt will run, init and exit
    val sbtLauncherOpts = List("early(startServer)")
    val sbtCommands = ""

    val projectSbtVersion = Version(detectSbtVersion(baseDir, getDefaultLauncher))
    val sbtVersion = upgradedSbtVersion(projectSbtVersion)
    val upgradeParam =
      if (sbtVersion > projectSbtVersion)
        List(sbtVersionParam(sbtVersion))
      else List.empty

    val vmArgs = SbtExternalSystemManager.getVmOptions(Seq.empty, jdkHome) ++ upgradeParam

    val dumper = new SbtStructureDump()
    val runInit = (reporter: BuildReporter) => dumper.runSbt(
      baseDir, jdkExe, vmArgs,
      Map.empty, sbtLauncher, Seq.empty, sbtLauncherOpts, sbtCommands,
      BspBundle.message("bsp.resolver.creating.sbt.configuration"),
    )(reporter)
    new SbtConfigSetup(dumper, runInit)
  }
}
