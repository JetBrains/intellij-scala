package org.jetbrains.plugins.scala.project.maven

import com.intellij.openapi.externalSystem.util.ExternalSystemApiUtil
import com.intellij.openapi.module.Module
import org.jetbrains.idea.maven.utils.MavenUtil
import org.jetbrains.sbt.project.BuildToolModuleHandler

//noinspection ApiStatus
private final class MavenModuleHandler extends BuildToolModuleHandler {
  override def handles(module: Module): Boolean =
    isNewMavenImporter(module) || isOldMavenImporter(module)

  private def isNewMavenImporter(module: Module): Boolean =
    MavenUtil.isMavenModule(module)

  private def isOldMavenImporter(module: Module): Boolean =
    ExternalSystemApiUtil.isExternalSystemAwareModule(MavenUtil.SYSTEM_ID, module)
}
