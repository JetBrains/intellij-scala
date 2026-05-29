package org.jetbrains.bsp.project.importing.setup

import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.platform.eel.{EelDescriptor, EelPlatformKt}
import org.jetbrains.bsp.BspUtil
import org.jetbrains.bsp.project.importing.setup.MillConfigSetup.*
import org.jetbrains.plugins.scala.extensions.PathExt
import org.jetbrains.sbt.eelDescriptor

import java.nio.file.Path
import scala.annotation.unused
import scala.util.{Failure, Success, Try}

/** Handles Mill BSP configuration generation. */
final class MillConfigSetup(workspace: Path) extends CommandBasedBspConfigSetup(workspace) {

  override protected def serverName: String = "Mill"

  protected type ConnectionTarget = Nothing

  override protected def installCommand(workspace: Path, indicator: ProgressIndicator, @unused target: Option[Nothing]): Try[Seq[String]] =
    getMillFile(workspace) match {
      case Some(file) =>
        Success(Seq(file.toCanonicalPath.toString, "-i", "mill.bsp.BSP/install"))
      //TODO: consider verifying Mill's installation in the #canImport to prevent its
      // display in BspSetupConfigStepUi if not installed (the same in ScalaCliProjectInstaller)
      //According to the docs, Mill global installation is only available for macOS/Linux.
      //https://mill-build.org/mill/cli/installation-ide.html#_global_installation
      case _ if !isWindows(workspace.eelDescriptor) && isMillInstalled(workspace, indicator) =>
        // If the launcher is not found in the project root but Mill is available in the PATH, then we can use it.
        Success(Seq("mill", "-i", "mill.bsp.BSP/install"))
      case _ => Failure(new IllegalStateException("Installation of BSP is unable to proceed as the Mill executable is missing from both the project root and the PATH."))
    }

  private def isMillInstalled(workspace: Path, indicator: ProgressIndicator): Boolean =
    BspUtil.isToolInstalledCheckViaVersion(workspace, indicator, "mill")
}

private[bsp] object MillConfigSetup {

  /**
   * File names that indicate a Mill project when present in the project root.
   * This list is not exhaustive (e.g., `build.mill.yaml` is missing) and could be improved in the future.
   */
  def buildFileNames(eelDescriptor: EelDescriptor): Seq[String] =
    Seq("build.mill", "build.mill.scala", getMillFileName(eelDescriptor))

  /**
   * Checks if the given workspace is a Mill project that can be imported.
   *
   * Notes: I think this method could be improved. Right now, it checks whether a Mill build file
   * or a Mill script is present in the project directory. I think that a project should be considered as "Mill"
   * only if it contains a Mill build file (`build.mill`/`build.mill.scala`/`build.mill.yaml`/more?),
   * because the wrapper script alone is not enough, especially considering that there might also be a global Mill installation.
   * However, it has existed in this form for some time, so I don't touch it to avoid breaking anything.
   */
  def canImport(workspace: Path): Boolean =
    workspace != null && workspace.isDirectory &&
      BspUtil.directoryContainsFile(workspace, buildFileNames(workspace.eelDescriptor)*)

  /** Get mill executable script, if exists. */
  private def getMillFile(workspace: Path): Option[Path] =
    BspUtil.findFileByName(workspace, getMillFileName(workspace.eelDescriptor))

  private def getMillFileName(eelDescriptor: EelDescriptor): String =
    if isWindows(eelDescriptor) then "mill.bat" else "mill"

  def isWindows(eelDescriptor: EelDescriptor): Boolean =
    EelPlatformKt.isWindows(eelDescriptor.getOsFamily)
}