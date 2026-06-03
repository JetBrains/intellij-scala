package org.jetbrains.plugins.scala.project.gradle

import com.intellij.openapi.externalSystem.util.ExternalSystemApiUtil
import com.intellij.openapi.module.Module
import org.jetbrains.plugins.gradle.util.GradleConstants
import org.jetbrains.sbt.project.BuildToolModuleHandler

//noinspection ApiStatus
private final class GradleModuleHandler extends BuildToolModuleHandler {
  override def handles(module: Module): Boolean =
    ExternalSystemApiUtil.isExternalSystemAwareModule(GradleConstants.SYSTEM_ID, module)
}
