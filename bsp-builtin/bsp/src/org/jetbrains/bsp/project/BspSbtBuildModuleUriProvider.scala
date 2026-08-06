package org.jetbrains.bsp.project

import com.intellij.openapi.module.Module
import org.jetbrains.plugins.scala.extensions.ObjectExt
import org.jetbrains.sbt.project.SbtBuildModuleUriProvider

import java.net.URI
import java.nio.file.Path

final class BspSbtBuildModuleUriProvider extends SbtBuildModuleUriProvider {

  override def getBuildModuleUri(module: Module): Option[URI] = {
    val sbtModuleData = BspExternalSystemUtil.getSbtModuleData(module)
    sbtModuleData.map(_.buildModuleId.uri)
  }

  override def getBuildModuleBaseDirectory(module: Module): Option[Path] = {
    val sbtModuleData = BspExternalSystemUtil.getSbtModuleData(module)
    sbtModuleData.flatMap(_.baseDirectory.toOption.map(_.toPath))
  }
}
