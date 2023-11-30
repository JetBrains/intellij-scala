package org.jetbrains.plugins.scala.bsp.config

import com.intellij.openapi.externalSystem.model.ProjectSystemId
object ScalaPluginConstants {
  val ID = "scalabsp"
  val SYSTEM_ID = new ProjectSystemId(ID, "Scala BSP")
  val WORKSPACE_FILE_NAMES: List[String] = List() // TODO: what are these? In bazel they have listOf("WORKSPACE", "WORKSPACE.bazel", "MODULE.bazel", "WORKSPACE.bzlmod")
  private val BUILD_FILE_NAMES = List("build.sbt")
  val SUPPORTED_EXTENSIONS: List[String] = List("scala", "sc")
  val SUPPORTED_CONFIG_FILE_NAMES: List[String] = WORKSPACE_FILE_NAMES ++ BUILD_FILE_NAMES


}
