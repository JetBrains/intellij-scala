package org.jetbrains.plugins.scala.bsp.assets

import com.intellij.openapi.util.IconLoader
import org.jetbrains.plugins.bsp.assets.BuildToolAssetsExtension
import org.jetbrains.plugins.bsp.extension.points.BuildToolId
import org.jetbrains.plugins.scala.bsp.config.ScalaPluginConstants

import javax.swing.Icon

class ScalaAssetsExtension extends BuildToolAssetsExtension {
  override def getIcon: Icon = IconLoader.getIcon("/org/jetbrains/plugins/scala/bsp/images/buildServerProtocol.svg", classOf[ScalaAssetsExtension])

  override def getLoadedTargetIcon: Icon = IconLoader.getIcon("/org/jetbrains/plugins/scala/bsp/images/buildServerProtocolTarget.svg", classOf[ScalaAssetsExtension])

  override def getInvalidTargetIcon: Icon = IconLoader.getIcon("/org/jetbrains/plugins/scala/bsp/images/buildServerProtocolTarget_red.svg", classOf[ScalaAssetsExtension])

  override def getUnloadedTargetIcon: Icon = IconLoader.getIcon("/org/jetbrains/plugins/scala/bsp/images/buildServerProtocolTarget_grey.svg", classOf[ScalaAssetsExtension])

  override def getPresentableName: String = "sbt"

  override def getBuildToolId: BuildToolId = ScalaPluginConstants.BUILD_TOOL_ID
}
