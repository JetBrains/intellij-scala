package org.jetbrains.bsp.project

import com.intellij.openapi.module.Module
import org.jetbrains.plugins.scala.extensions.ObjectExt
import org.jetbrains.sbt.project.SbtBuildModuleUriProvider

import java.io.File
import java.net.URI

final class BspSbtBuildModuleUriProvider extends SbtBuildModuleUriProvider {

  override def getBuildModuleUri(module: Module): Option[URI] = {
    val sbtModuleData = BspExternalSystemUtil.getSbtModuleData(module)
    sbtModuleData.map(_.buildModuleId.uri)
  }

  override def getBuildModuleBaseDirectory(module: Module): Option[File] = {
    val sbtModuleData = BspExternalSystemUtil.getSbtModuleData(module)
    sbtModuleData.flatMap(_.baseDirectory.toOption)
  }
}
