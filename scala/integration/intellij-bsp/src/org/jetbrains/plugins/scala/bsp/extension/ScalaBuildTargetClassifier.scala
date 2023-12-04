package org.jetbrains.plugins.scala.bsp.extension

import org.jetbrains.plugins.bsp.extension.points.{BuildTargetClassifierExtension, BuildToolId}
import org.jetbrains.plugins.scala.bsp.config.ScalaPluginConstants

import java.util
import scala.jdk.CollectionConverters._

private class ScalaBuildTargetClassifier extends BuildTargetClassifierExtension {
  override val getBuildToolId: BuildToolId = ScalaPluginConstants.BUILD_TOOL_ID
  override val getSeparator = "/"
  private val sbtLabelRegex = """@?@?(?<repository>.*)//(?<package>.*):(?<target>.*)""".r

  override def calculateBuildTargetPath(s: String): util.List[String] =
    sbtLabelRegex.findFirstMatchIn(s)
      .map {
        _.group("package")
          .split("/")
          .filter {
            _.nonEmpty
          }
      }
      .getOrElse(Array.empty[String])
      .toList
      .asJava

  override def calculateBuildTargetName(s: String): String =
    sbtLabelRegex.findFirstMatchIn(s)
      .map {
        _.group("target")
      }
      .getOrElse(s)
}

