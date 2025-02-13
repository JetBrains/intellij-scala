package org.jetbrains.bsp.project.importing

import com.intellij.openapi.util.SystemInfo
import org.jetbrains.bsp.BspUtil
import org.jetbrains.bsp.project.BspProjectInstallProvider
import org.jetbrains.bsp.project.importing.bspConfigSteps.{ConfigSetup, MillSetup}

import java.io.File
import scala.io.Source
import scala.sys.process._
import scala.util.{Failure, Success, Try, Using}

class MillProjectInstaller extends BspProjectInstallProvider {

  private val versionPattern = """^.*(0\.8\.0|0\.7.+|0\.6.+)$"""

  override def canImport(workspace: File): Boolean =
    Option(workspace) match {
      case Some(directory) if directory.isDirectory =>
        BspUtil.directoryContainsFile(directory, "build.mill", "build.mill.scala") ||
          isBspCompatible(directory) ||
          isLegacyBspCompatible(directory)
      case _ => false
    }

  override def getConfigSetup: ConfigSetup = MillSetup

  override def serverName: String = "Mill"

  override def installCommand(workspace: File): Try[Seq[String]] = {
    // note: The legacy part is only executed for mill bootstrap script so it is not applicable for Windows.
    // Maybe it could be, but we decided to support mill.bat file only for the newer bsp approach
    val isLegacyMill = !SystemInfo.isWindows && isLegacyBspCompatible(workspace)
    val millFileOpt = getMillFile(workspace)
    millFileOpt match {
      case Some(file) if isLegacyMill && !isMillFileBspCompatible(file, workspace) =>
        // run this only if we're confident this is legacy Mill
        Success(Seq(file.getAbsolutePath, "-i", "mill.contrib.BSP/install"))
      case Some(file) =>
        // otherwise run the normal BSP install command
        Success(Seq(file.getAbsolutePath, "-i", "mill.bsp.BSP/install"))
      //TODO: consider verifying Mill's installation in the #canImport to prevent its
      // display in BspSetupConfigStepUi if not installed (the same in ScalaCliProjectInstaller)
      case _ if isMillInstalled(workspace) =>
        // If the launcher is not found in the project root but Mill is available in the PATH, then we can use it.
        Success(Seq("mill", "-i", "mill.bsp.BSP/install"))
      case _ => Failure(new IllegalStateException("Installation of BSP is unable to proceed as the Mill executable is missing from both the project root and the PATH."))
    }
  }

  private def isMillInstalled(workspace: File): Boolean =
    BspUtil.checkIfToolIsInstalled(workspace.toPath, "mill")

  private def getMillFile(workspace: File): Option[File] =
    if (SystemInfo.isWindows) BspUtil.findFileByName(workspace, "mill.bat")
    else BspUtil.findFileByName(workspace, "mill")

  private def checkMillVersionWithBatFile(file: File, workspace: File): Boolean = {
    val stdout = new StringBuilder
    val versionCommand = s"${file.getAbsolutePath} --version"
    Process(versionCommand, workspace) ! ProcessLogger(stdout append _ + "\n", _ => ())

    stdout.toString()
      .linesIterator
      .exists { line =>
        line.contains("Mill Build Tool version") && !line.matches(versionPattern)
      }
  }

  private def isBspCompatible(workspace: File) = {
    val fileOpt = getMillFile(workspace)
    fileOpt.exists(isMillFileBspCompatible(_, workspace))
  }

  /**
   This method checks whether the Mill version is not a legacy (it is higher that  0.8.0).
   */
  private def isMillFileBspCompatible(millFile: File, workspace: File): Boolean = {
    if (SystemInfo.isWindows) {
      checkMillVersionWithBatFile(millFile, workspace)
    } else {
      Using.resource(Source.fromFile(millFile)) { source =>
        source
          .getLines()
          .exists(t => !t.matches(versionPattern))
      }
    }
  }

  // Legacy Mill =< 0.8.0
  private def isLegacyBspCompatible(workspace: File) =
    BspUtil.findFileByName(workspace, "build.sc").exists { buildScript =>
      Using.resource(Source.fromFile(buildScript))(
        _.getLines().contains("import $ivy.`com.lihaoyi::mill-contrib-bsp:$MILL_VERSION`")
      )
    }

}
