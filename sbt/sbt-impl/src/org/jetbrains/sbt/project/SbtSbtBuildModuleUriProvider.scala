package org.jetbrains.sbt.project

import com.intellij.openapi.module.Module
import org.jetbrains.sbt.SbtUtil

import java.net.URI

final class SbtSbtBuildModuleUriProvider extends SbtBuildModuleUriProvider {
  override def getBuildModuleUri(module: Module): Option[URI] = {
    val sbtModuleData = SbtUtil.getSbtModuleData(module)
    sbtModuleData.map(_.buildURI.uri)
  }
}
