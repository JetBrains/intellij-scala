package org.jetbrains.plugins.scala.bazel

import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.platform.backend.workspace.WorkspaceModel
import com.intellij.platform.workspace.jps.entities.ModuleEntity
import com.intellij.platform.workspace.storage.MutableEntityStorage
import com.intellij.platform.workspace.storage.url.VirtualFileUrl
import org.jetbrains.bazel.config.ProjectKt
import org.jetbrains.bazel.sync.ProjectPostSyncHook
import org.jetbrains.bazel.workspacemodel.entities.{BazelProjectEntitySource, ScalaAddendumEntityKt}
import org.jetbrains.plugins.scala.compiler.data.CompileOrder
import org.jetbrains.plugins.scala.project.external.ScalaSdkUtils
import org.jetbrains.plugins.scala.project.{ModuleEntityExt, ReplClasspath}

import java.nio.file.Path
import kotlin.coroutines.Continuation
import scala.jdk.CollectionConverters.{CollectionHasAsScala, IteratorHasAsScala}

class BazelScalaModuleConfiguratorPostSyncHook extends ProjectPostSyncHook {

  override def isEnabled(project: Project): Boolean = ProjectKt.isBazelProject(project)

  override def onPostSync(projectPostSyncHookEnvironment: ProjectPostSyncHook.ProjectPostSyncHookEnvironment, continuation: Continuation[? >: kotlin.Unit]): AnyRef = {
    val project = projectPostSyncHookEnvironment.getProject
    val scalaModuleEntities =
      WorkspaceModel
        .getInstance(project)
        .getCurrentSnapshot
        .entities(classOf[ModuleEntity])
        .iterator()
        .asScala
        .filter { moduleEntity => ScalaAddendumEntityKt.getScalaAddendumEntity(moduleEntity) != null }

    WorkspaceModel.getInstance(project).update("Update Scala modules for Bazel project",
      (storage: MutableEntityStorage) => {
        scalaModuleEntities.foreach { entity =>
          val scalaAddendumEntity = ScalaAddendumEntityKt.getScalaAddendumEntity(entity)
          ScalaSdkUtils.configureScalaSdk(
            module = entity,
            compilerVersion = scalaAddendumEntity.getCompilerVersion,
            scalacClasspath = scalaAddendumEntity.getSdkClasspaths.asScala
              .map(virtualFileUrl => virtualFileUrlToPath(virtualFileUrl))
              .collect { case Some(path) => path }
              .toSeq,
            scaladocExtraClasspath = Nil,
            compilerBridgeBinaryJar = None,
            replClasspath = ReplClasspath.Bundled,
            sdkPrefix = "Bazel",
            storage = storage,
            entitySource = BazelProjectEntitySource.INSTANCE,
          )
          entity.configureScalaCompilerSettingsFrom(project, "bazel", scalaAddendumEntity.getScalacOptions.asScala.toSeq, CompileOrder.Mixed)
        }
        kotlin.Unit.INSTANCE
      }, continuation)
  }

  private def virtualFileUrlToPath(virtualFileUrl: VirtualFileUrl): Option[Path] =
    Option(VirtualFileManager.getInstance().findFileByUrl(virtualFileUrl.getUrl)).map(_.toNioPath)
}
