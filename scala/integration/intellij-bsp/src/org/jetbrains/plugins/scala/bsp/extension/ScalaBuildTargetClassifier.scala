package org.jetbrains.plugins.scala.bsp.extension

import com.intellij.openapi.diagnostic.Logger
import org.jetbrains.magicmetamodel.impl.workspacemodel.BuildTargetInfo
import org.jetbrains.plugins.bsp.extension.points.{BuildTargetClassifierExtension, BuildToolId}
import org.jetbrains.plugins.scala.bsp.config.ScalaPluginConstants

import java.util
import scala.jdk.CollectionConverters._

private class ScalaBuildTargetClassifier extends BuildTargetClassifierExtension {
  private val logger = Logger.getInstance(this.getClass)

  private val buildTargetFileRegex = """(file:/)(.*)/(#[^/]*/[^/]*)""".r
  override val getBuildToolId: BuildToolId = ScalaPluginConstants.BUILD_TOOL_ID
  override val getSeparator = "/"

  override def calculateBuildTargetName(buildTargetInfo: BuildTargetInfo): String = {
    buildTargetInfo.getDisplayName
  }

  override def calculateBuildTargetPath(buildTargetInfo: BuildTargetInfo): util.List[String] = {
    val buildTarget = buildTargetInfo.getId

    buildTarget match {
      case buildTargetFileRegex(_, path, _) =>
        path.split("/").toList.asJava
      case _ =>
        logger.warn(s"buildTargetFileRegex mismatch, s=$buildTarget")
        List.empty.asJava
    }
  }
}
