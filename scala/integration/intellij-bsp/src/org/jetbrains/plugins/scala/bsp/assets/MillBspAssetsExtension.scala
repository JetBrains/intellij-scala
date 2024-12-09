package org.jetbrains.plugins.scala.bsp.assets

import com.intellij.openapi.util.IconLoader
import org.jetbrains.plugins.bsp.assets.BuildToolAssetsExtension
import org.jetbrains.plugins.bsp.config.BuildToolId
import org.jetbrains.plugins.scala.bsp.MillBspBundle
import org.jetbrains.plugins.scala.bsp.config.MillBspPluginConstants

import javax.swing.Icon

class MillBspAssetsExtension extends BuildToolAssetsExtension {
  override def getToolWindowIcon: Icon = IconLoader.getIcon("icons/buildServerProtocol.svg", classOf[MillBspAssetsExtension])

  override def getErrorTargetIcon: Icon = IconLoader.getIcon("icons/buildServerProtocolTarget_red.svg", classOf[MillBspAssetsExtension])

  override def getPresentableName: String = MillBspBundle.message("mill.bsp")

  override def getBuildToolId: BuildToolId = MillBspPluginConstants.BUILD_TOOL_ID

  override def getTargetIcon: Icon = IconLoader.getIcon("icons/buildServerProtocolTarget.svg", classOf[MillBspAssetsExtension])

}