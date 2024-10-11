package org.jetbrains.plugins.scala.bsp.config

import com.intellij.openapi.externalSystem.model.ProjectSystemId
import org.jetbrains.plugins.bsp.config.BuildToolId
import org.jetbrains.plugins.scala.bsp.MillBspBundle

object MillScalaPluginConstants {
  val ID = "scala-mill-bsp"
  val BUILD_TOOL_ID: BuildToolId = new BuildToolId(MillBspBundle.message("mill.bsp"))
  val SYSTEM_ID = new ProjectSystemId(ID, MillBspBundle.message("scala.mill.bsp"))
  val SUPPORTED_CONFIG_FILE_EXTENSIONS: List[String] = List("sc")
  val SUPPORTED_CONFIG_FILE_NAMES: List[String] = List("build")
  val MILL_CONFIG_FILE = "build.sc"
  val BSP_CONNECTION_DIR = ".bsp"
}