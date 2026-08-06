package org.jetbrains.plugins.scala.bazel

import com.intellij.openapi.project.Project
import com.intellij.util.JavaCoroutines
import org.jetbrains.bazel.config.ProjectKt
import org.jetbrains.bazel.sync.ProjectPostSyncHook
import org.jetbrains.plugins.scala.compiler.data.IncrementalityType
import org.jetbrains.plugins.scala.project.settings.ScalaCompilerConfiguration
import org.jetbrains.plugins.scala.settings.ScalaProjectSettings

import kotlin.coroutines.Continuation

class ScalaProjectSettingsConfigurePostSyncHook extends ProjectPostSyncHook {

  override def isEnabled(project: Project): Boolean = ProjectKt.isBazelProject(project)

  override def onPostSync(projectPostSyncHookEnvironment: ProjectPostSyncHook.ProjectPostSyncHookEnvironment, continuation: Continuation[? >: kotlin.Unit]): AnyRef = {
    val project = projectPostSyncHookEnvironment.getProject
    //noinspection ApiStatus,UnstableApiUsage
    JavaCoroutines.suspendJava[kotlin.Unit](cont => {
      // compiler highlighting does not work with Bazel projects as the JPS build is not automatically run each sync
      val scalaProjectSettings = ScalaProjectSettings.in(project)
      scalaProjectSettings.setCompilerHighlightingScala2(false)
      scalaProjectSettings.setCompilerHighlightingScala3(false)
      // Bazel projects have issues when compiling with Zinc, hence this enforcement.
      // SCL-23923 Customers' project failed to locally build with Zinc
      ScalaCompilerConfiguration.instanceIn(project).incrementalityType = IncrementalityType.IDEA
      cont.resume(kotlin.Unit.INSTANCE)
    }, continuation)
  }
}
