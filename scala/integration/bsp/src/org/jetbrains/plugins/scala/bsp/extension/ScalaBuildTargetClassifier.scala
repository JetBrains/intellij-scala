package org.jetbrains.plugins.scala.bsp.extension

import org.jetbrains.plugins.bsp.extension.points.BspBuildTargetClassifierExtension
import org.jetbrains.plugins.scala.bsp.config.ScalaPluginConstants

import java.util
import scala.jdk.CollectionConverters._

private class ScalaBuildTargetClassifier extends BspBuildTargetClassifierExtension {
  override val name: String = ScalaPluginConstants.SYSTEM_ID.getId

  override val separator: String = "/"

  private val sbtLabelRegex = """@?@?(?<repository>.*)//(?<package>.*):(?<target>.*)""".r

  override def getBuildTargetPath(s: String): util.List[String] =
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

  override def getBuildTargetName(s: String): String =
    sbtLabelRegex.findFirstMatchIn(s)
      .map {
        _.group("target")
      }
      .getOrElse(s)
}

