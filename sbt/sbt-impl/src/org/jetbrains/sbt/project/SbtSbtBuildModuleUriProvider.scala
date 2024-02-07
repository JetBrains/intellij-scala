package org.jetbrains.sbt.project

import com.intellij.openapi.module.Module
import org.jetbrains.sbt.SbtUtil

import java.io.File
import java.net.URI

final class SbtSbtBuildModuleUriProvider extends SbtBuildModuleUriProvider {
  override def getBuildModuleUri(module: Module): Option[URI] = {
    val sbtModuleEntity = SbtUtil.getSbtModuleEntity(module)
    sbtModuleEntity.map(entity => new URI(entity.getBuildURI))
  }

  override def getBuildModuleBaseDirectory(module: Module): Option[File] = {
    val sbtModuleEntity = SbtUtil.getSbtModuleEntity(module)
    sbtModuleEntity.map(entity => new File(entity.getBaseDirectory.getPresentableUrl))
  }
}
