package org.jetbrains.bsp.protocol

import java.io.File

import ch.epfl.scala.bsp4j.BspConnectionDetails
import com.google.gson.Gson
import com.intellij.openapi.util.SystemInfo
import com.intellij.openapi.util.text.StringUtil.defaultIfEmpty
import com.intellij.util.SystemProperties
import org.jetbrains.bsp.{BspBundle, BspErrorMessage}

import scala.io.Source
import scala.util.{Failure, Try}

object BspConnectionConfig {

  val BspWorkspaceConfigDirName = ".bsp"
  
  private val BspSystemConfigDirName = "bsp"

  def workspaceConfigurationFiles(workspace: File): List[File] = {
    val bspDir = new File(workspace, BspWorkspaceConfigDirName)
    if(bspDir.isDirectory) {
      val files = bspDir.listFiles(file => file.getName.endsWith(".json"))
      if (files != null) files.toList else Nil
    }
    else List.empty
  }

  /** Find all BSP connection configs for a workspace. */
  def workspaceBspConfigs(workspace: File): List[(File, BspConnectionDetails)] = {
    val files = workspaceConfigurationFiles(workspace)
    tryReadingConnectionFiles(files).flatMap(_.toOption).toList
  }

  /** Find all BSP connection configs either in a workspace, or installed on a system. */
  def allBspConfigs(workspace: File): List[(File, BspConnectionDetails)] = {

    val workspaceConfigs = workspaceConfigurationFiles(workspace)
    val systemConfigs = systemDependentConnectionFiles
    val potentialConfigs = tryReadingConnectionFiles(workspaceConfigs ++ systemConfigs)

    potentialConfigs.flatMap(_.toOption).toList
  }

  def isBspConfigFile(file: File): Boolean = {
    file.isFile &&
      file.getParentFile.getName == BspWorkspaceConfigDirName && 
      file.getName.endsWith(".json")
  }

  /**
   * Find connection files installed on user's system.
   * https://build-server-protocol.github.io/docs/server-discovery.html#default-locations-for-bsp-connection-files
   */
  private def systemDependentConnectionFiles: List[File] = {
    val basePaths =
      if (SystemInfo.isWindows) windowsBspFiles()
      else if (SystemInfo.isMac) macBspFiles()
      else if (SystemInfo.isUnix) unixBspFiles()
      else Nil

    listFiles(bspDirs(basePaths))
  }

  private def tryReadingConnectionFiles(files: Seq[File]): Seq[Try[(File, BspConnectionDetails)]] = {
    implicit val gson: Gson = new Gson()
    files.map { f => readConnectionFile(f).map((f, _)) }
  }

  def readConnectionFile(file: File)(implicit gson: Gson): Try[BspConnectionDetails] = {
    if (file.canRead) {
      val reader = Source.fromFile(file).bufferedReader()
      Try(gson.fromJson(reader, classOf[BspConnectionDetails]))
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

  private def bspDirs(basePaths: List[String]): List[File] = basePaths.map(new File(_, BspSystemConfigDirName))

  private def listFiles(dirs: List[File]): List[File] = dirs.flatMap { dir =>
    if (dir.isDirectory) dir.listFiles()
    else Array.empty[File]
  }
  
}
