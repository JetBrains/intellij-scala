package org.jetbrains.plugins.scala.bazel

import com.intellij.openapi.module.Module
import org.jetbrains.bazel.config.BazelProjectPropertiesKt
import org.jetbrains.sbt.project.BuildToolModuleHandler

import scala.annotation.nowarn

//noinspection ApiStatus
private final class BazelModuleHandler extends BuildToolModuleHandler {
  override def handles(module: Module): Boolean =
    BazelProjectPropertiesKt.isBazelProject(module.getProject): @nowarn("cat=deprecation")
}
