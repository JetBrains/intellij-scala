package org.jetbrains.plugins.scala.bsp.extension

import com.intellij.openapi.diagnostic.Logger
import org.jetbrains.magicmetamodel.impl.workspacemodel.BuildTargetInfo
import org.jetbrains.plugins.bsp.extension.points.{BuildTargetClassifierExtension, BuildToolId}
import org.jetbrains.plugins.scala.bsp.config.ScalaPluginConstants

import java.io.File
import java.util
import scala.jdk.CollectionConverters._

private class ScalaBuildTargetClassifier extends BuildTargetClassifierExtension {
  override def getSeparator: String = "."
  override val getBuildToolId: BuildToolId = ScalaPluginConstants.BUILD_TOOL_ID

  override def calculateBuildTargetName(buildTargetInfo: BuildTargetInfo): String = {
    val splitPath = buildTargetInfo.getId.split(File.separator)
    val (_, targetSegment) = splitPath.span(segment => !segment.startsWith("#"))

    if (targetSegment.length == 1 && targetSegment.head.endsWith("-build")) {
      "Build"
    } else if (targetSegment.length == 2) {
      targetSegment(1)
    } else {
      throw new RuntimeException(f"Can't parse buildTargetInfoId = ${buildTargetInfo.getId}")
    }
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

    util.List.of(formattedDisplayName)
  }

}
