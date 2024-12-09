package org.jetbrains.plugins.scala.bsp.extension

import org.jetbrains.plugins.bsp.config.BuildToolId
import org.jetbrains.plugins.bsp.extensionPoints.BuildTargetClassifierExtension
import org.jetbrains.plugins.bsp.workspacemodel.entities.BuildTargetInfo
import org.jetbrains.plugins.scala.bsp.config.MillBspPluginConstants

import java.io.File
import java.util

class MillBspBuildTargetClassifier extends BuildTargetClassifierExtension {

  override def getSeparator: String = "."

  override val getBuildToolId: BuildToolId = MillBspPluginConstants.BUILD_TOOL_ID

  override def calculateBuildTargetName(buildTargetInfo: BuildTargetInfo): String = {
    val splitPath = buildTargetInfo.getId.toString.split(File.separator)
    splitPath.last.split("=", 2).last.replaceAll("[^a-zA-Z0-9]+$", "")
  }

  override def calculateBuildTargetPath(buildTargetInfo: BuildTargetInfo): util.List[String] =
    util.List.of() // We don't want any path displayed in the build target view
}
