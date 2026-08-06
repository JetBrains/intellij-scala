package org.jetbrains.sbt.project

import com.intellij.openapi.module.Module
import org.jetbrains.sbt.WorkspaceModelUtil

import java.net.URI
import java.nio.file.Path

final class SbtSbtBuildModuleUriProvider extends SbtBuildModuleUriProvider {
  override def getBuildModuleUri(module: Module): Option[URI] = {
    val sbtModuleEntity = WorkspaceModelUtil.getSbtModuleEntity(module)
    sbtModuleEntity.map(entity => new URI(entity.getBuildURI))
  }

  override def getBuildModuleBaseDirectory(module: Module): Option[Path] = {
    val sbtModuleEntity = WorkspaceModelUtil.getSbtModuleEntity(module)
    sbtModuleEntity.map(entity => Path.of(entity.getBaseDirectory.getPresentableUrl))
  }
}
