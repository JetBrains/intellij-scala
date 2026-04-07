package org.jetbrains.bsp.project.importing.setup

import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.util.SystemInfo
import org.jetbrains.bsp.BspUtil
import org.jetbrains.bsp.project.importing.setup.MillConfigSetup._
import org.jetbrains.plugins.scala.extensions.PathExt

import java.nio.charset.Charset
import java.nio.file.{Files, Path}
import scala.annotation.unused
import scala.util.{Failure, Success, Try, Using}

/** Handles Mill BSP configuration generation. */
final class MillConfigSetup(workspace: Path) extends CommandBasedBspConfigSetup(workspace) {

  override protected def serverName: String = "Mill"

  protected type ConnectionTarget = Nothing

  override protected def installCommand(workspace: Path, indicator: ProgressIndicator, @unused target: Option[Nothing]): Try[Seq[String]] = {
    // note: The legacy part is only executed for mill bootstrap script so it is not applicable for Windows.
    // Maybe it could be, but we decided to support mill.bat file only for the newer bsp approach
    val isLegacyMill = !SystemInfo.isWindows && isLegacyBspCompatible(workspace)
    val millFileOpt = getMillFile(workspace)
    millFileOpt match {
      case Some(file) if isLegacyMill && !isMillFileBspCompatible(file) =>
        // run this only if we're confident this is legacy Mill
        Success(Seq(file.toCanonicalPath.toString, "-i", "mill.contrib.BSP/install"))
      case Some(file) =>
        // otherwise run the normal BSP install command
        Success(Seq(file.toCanonicalPath.toString, "-i", "mill.bsp.BSP/install"))
      //TODO: consider verifying Mill's installation in the #canImport to prevent its
      // display in BspSetupConfigStepUi if not installed (the same in ScalaCliProjectInstaller)
      //According to the docs, Mill global installation is only available for macOS/Linux.
      //https://mill-build.org/mill/cli/installation-ide.html#_global_installation
      case _ if !SystemInfo.isWindows && isMillInstalled(workspace, indicator) =>
        // If the launcher is not found in the project root but Mill is available in the PATH, then we can use it.
        Success(Seq("mill", "-i", "mill.bsp.BSP/install"))
      case _ => Failure(new IllegalStateException("Installation of BSP is unable to proceed as the Mill executable is missing from both the project root and the PATH."))
    }
  }

  private def isMillInstalled(workspace: Path, indicator: ProgressIndicator): Boolean =
    BspUtil.isToolInstalledCheckViaVersion(workspace, indicator, "mill")
}

private[bsp] object MillConfigSetup {

  private val versionPattern = """^.*(0\.8\.0|0\.7.+|0\.6.+)$"""

  /** Checks if the given workspace is a Mill project that can be imported. */
  def canImport(workspace: Path): Boolean =
    Option(workspace) match {
      case Some(directory) if directory.isDirectory =>
        BspUtil.directoryContainsFile(directory, "build.mill", "build.mill.scala") ||
          isBspCompatible(directory) ||
          isLegacyBspCompatible(directory)
      case _ => false
    }

  /** Get mill executable script, if exists. */
  private def getMillFile(workspace: Path): Option[Path] =
    if (SystemInfo.isWindows) BspUtil.findFileByName(workspace, "mill.bat")
    else BspUtil.findFileByName(workspace, "mill")

  private def isBspCompatible(workspace: Path): Boolean = {
    val fileOpt = getMillFile(workspace)
    fileOpt.exists(isMillFileBspCompatible)
  }

  // LEGACY:
  // This logic appears to be unreliable and may not work correctly.
  // It checks whether any line in the Mill script does not match the legacy version pattern (0.6.x, 0.7.x, 0.8.0).
  // As I checked, in e.g., 0.7.0 version, the first line of the script is a shebang line (`#!/usr/bin/env sh`),
  // which never matches `versionPattern`, so `anyMatch` with `!t.matches(versionPattern)` always returns true.
  // To check this, you can download the script for the 0.7.0 version with `sudo curl -L https://github.com/lihaoyi/mill/releases/download/0.7.0/0.7.0`.
  // As a result, this method effectively always returns true for those versions, making the check meaningless.
  // I don't know, maybe it used to work properly for some older versions.
  /**
   * Checks whether the Mill version is not a legacy (it is higher than 0.8.0).
   * For a Windows system, it always returns `true` because we agreed to not support Mill legacy with the Windows `mill.bat` file.
   */
  private def isMillFileBspCompatible(millFile: Path): Boolean =
    SystemInfo.isWindows || Using.resource(Files.lines(millFile, Charset.defaultCharset())) { lines =>
      lines.anyMatch(t => !t.matches(versionPattern))
    }

  // Legacy Mill =< 0.8.0
  private def isLegacyBspCompatible(workspace: Path): Boolean =
    BspUtil.findFileByName(workspace, "build.sc").exists { buildScript =>
      Using.resource(Files.lines(buildScript, Charset.defaultCharset()))(
        _.anyMatch(line => line == "import $ivy.`com.lihaoyi::mill-contrib-bsp:$MILL_VERSION`")
      )
    }
}