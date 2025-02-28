package org.jetbrains.bsp.protocol

import ch.epfl.scala.bsp4j.BspConnectionDetails
import com.google.gson.Gson
import com.intellij.openapi.util.SystemInfo
import com.intellij.openapi.util.text.StringUtil.defaultIfEmpty
import com.intellij.util.SystemProperties
import org.jetbrains.bsp.{BspBundle, BspErrorMessage}
import org.jetbrains.plugins.scala.extensions.PathExt

import java.nio.file.{Files, Path}
import scala.util.{Failure, Try, Using}

object BspConnectionConfig {

  val BspWorkspaceConfigDirName = ".bsp"
  
  private val BspSystemConfigDirName = "bsp"

  def workspaceConfigurationFiles(workspace: Path): List[Path] = {
    val bspDir = workspace.resolve(BspWorkspaceConfigDirName)
    if(bspDir.isDirectory) {
      bspDir.children().filter(_.getFileName.toString.endsWith(".json")).toList
    }
    else List.empty
  }
  
  private[protocol] def workspaceBspConfigsHash(workspace: Path): Int = {
    workspaceBspConfigs(workspace).map(f => f.hashCode()).sum
  }

  /** Find all BSP connection configs for a workspace. */
  def workspaceBspConfigs(workspace: Path): List[(Path, BspConnectionDetails)] = {
    val files = workspaceConfigurationFiles(workspace)
    tryReadingConnectionFiles(files).flatMap(_.toOption).toList
  }

  /** Find all BSP connection configs either in a workspace, or installed on a system. */
  def allBspConfigs(workspace: Path): List[(Path, BspConnectionDetails)] = {

    val workspaceConfigs = workspaceConfigurationFiles(workspace)
    val systemConfigs = systemDependentConnectionFiles
    val potentialConfigs = tryReadingConnectionFiles(workspaceConfigs ++ systemConfigs)

    potentialConfigs.flatMap(_.toOption).toList
  }

  def isBspConfigFile(file: Path): Boolean = {
    file.isRegularFile &&
      file.getParent.getFileName.toString == BspWorkspaceConfigDirName &&
      file.getFileName.toString.endsWith(".json")
  }

  /**
   * Find connection files installed on user's system.
   * https://build-server-protocol.github.io/docs/server-discovery.html#default-locations-for-bsp-connection-files
   */
  private def systemDependentConnectionFiles: List[Path] = {
    val basePaths =
      if (SystemInfo.isWindows) windowsBspFiles()
      else if (SystemInfo.isMac) macBspFiles()
      else if (SystemInfo.isUnix) unixBspFiles()
      else Nil

    listFiles(bspDirs(basePaths))
  }

  private def tryReadingConnectionFiles(files: Seq[Path]): Seq[Try[(Path, BspConnectionDetails)]] = {
    val gson: Gson = new Gson()
    files.map { f => readConnectionFile(f)(using gson).map((f, _)) }
  }

  def readConnectionFile(file: Path)(implicit gson: Gson): Try[BspConnectionDetails] = {
    if (Files.isReadable(file)) {
      Using(Files.newBufferedReader(file)) { reader =>
        gson.fromJson(reader, classOf[BspConnectionDetails])
      }
    } else Failure(BspErrorMessage(BspBundle.message("bsp.protocol.file.not.readable", file)))
  }

  private def windowsBspFiles() = {
    val localAppData = System.getenv("LOCALAPPDATA")
    val programData = System.getenv("PROGRAMDATA")
    List(localAppData, programData)
  }

  private def unixBspFiles() = {
    val xdgDataHome = System.getenv("XDG_DATA_HOME")
    val xdgDataDirs = System.getenv("XDG_DATA_DIRS")
    val dataHome = defaultIfEmpty(xdgDataHome, SystemProperties.getUserHome + "/.local/share")
    val dataDirs = defaultIfEmpty(xdgDataDirs, "/usr/local/share:/usr/share").split(":").toList
    dataHome :: dataDirs
  }

  private def macBspFiles() = {
    val userHome = SystemProperties.getUserHome
    val userData = userHome + "/Library/Application Support"
    val systemData = "/Library/Application Support"
    List(userData, systemData)
  }

  private def bspDirs(basePaths: List[String]): List[Path] = basePaths.map(bp => Path.of(bp).resolve(BspSystemConfigDirName))

  private def listFiles(dirs: List[Path]): List[Path] = dirs.flatMap { dir =>
    if (dir.isDirectory) dir.children()
    else Seq.empty[Path]
  }
}
