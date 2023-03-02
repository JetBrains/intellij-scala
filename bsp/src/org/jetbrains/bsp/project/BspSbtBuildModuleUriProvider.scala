package org.jetbrains.bsp.project

import com.intellij.openapi.module.Module
import org.jetbrains.sbt.project.SbtBuildModuleUriProvider

import java.net.URI

final class BspSbtBuildModuleUriProvider extends SbtBuildModuleUriProvider {

  override def getBuildModuleUri(module: Module): Option[URI] = {
    val sbtModuleData = BspExternalSystemUtil.getSbtModuleData(module)
    sbtModuleData.map(_.buildModuleId.uri)
  }
}
