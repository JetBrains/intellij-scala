package org.jetbrains.plugins.scala.bsp.extension

import org.jetbrains.plugins.bsp.config.BuildToolId
import org.jetbrains.plugins.bsp.extensionPoints.BuildTargetClassifierExtension
import org.jetbrains.plugins.bsp.workspacemodel.entities.BuildTargetInfo
import org.jetbrains.plugins.scala.bsp.config.MillScalaPluginConstants

import java.io.File
import java.util

class MillScalaBuildTargetClassifier extends BuildTargetClassifierExtension {
  override def getSeparator: String = "."
  override val getBuildToolId: BuildToolId = MillScalaPluginConstants.BUILD_TOOL_ID

  override def calculateBuildTargetName(buildTargetInfo: BuildTargetInfo): String = {
    def stripSuffix(input: String): String = {
      input.replaceAll("[^a-zA-Z0-9]+$", "")
    }
    val splitPath = buildTargetInfo.getId.toString.split(File.separator)
    stripSuffix(splitPath.last)
  }

  override def calculateBuildTargetPath(buildTargetInfo: BuildTargetInfo): util.List[String] = {
    val displayName = buildTargetInfo.getDisplayName
    val formattedDisplayName = if (displayName.endsWith("-test")) {
      displayName.stripSuffix("-test")
    } else if (displayName.endsWith("-build")) {
      displayName.stripSuffix("-build")
    } else {
      displayName
    }
    println(util.List.of(formattedDisplayName))

    util.List.of(formattedDisplayName)
  }

}
