package org.jetbrains.plugins.scala.compiler.highlighting

import com.intellij.compiler.server.{BuildManager, OptionsDirectoryProcessor}
import com.intellij.openapi.application.PathManager
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.{Project, ProjectUtil, ProjectUtilCore}
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.platform.eel.provider.EelProviderUtil
import com.intellij.platform.eel.provider.utils.EelPathUtils
import com.intellij.util.io.PathKt
import org.jetbrains.jps.incremental.Utils
import org.jetbrains.jps.incremental.scala.Client
import org.jetbrains.jps.incremental.scala.remote.{CompileServerCommand, SourceScope}
import org.jetbrains.plugins.scala.compiler.{CompileServerClient, CompileServerLauncher, ProjectMetadataUtil}

import java.nio.file.Path

private object IncrementalCompiler {

  def compile(project: Project, modules: Set[Module], sourceScope: SourceScope, client: Client): Unit = {
    val projectPath = Option(project.getPresentableUrl)
      .map(VirtualFileManager.extractPath)
      .map(Path.of(_))
      .getOrElse(throw new IllegalStateException("Can't determine project path"))

    //noinspection ApiStatus,UnstableApiUsage
    val globalOptionsPath =
      if (EelPathUtils.isPathLocal(projectPath))
        PathManager.getOptionsDir
      else
        OptionsDirectoryProcessor.transferOptionsToRemote(PathManager.getOptionsDir, project)

    val rootPath = Path.of(PathKt.getSystemIndependentPath(BuildManager.getInstance.getBuildSystemDirectory(project)))
    // JPS Utils.getDataStorageRoot only accepts/returns java.io.File; there is no nio.Path-based alternative.
    //noinspection SSBasedInspection
    val dataStorageRootPath = Utils.getDataStorageRoot(rootPath.toFile, projectPath.toString).toPath

    /** @see `org.jetbrains.jps.incremental.scala.remote.Main.withModifiedExternalProjectPath` */
    val externalConfigurationDir =
      if (ProjectUtilCore.isExternalStorageEnabled(project)) {
        // The implementation was created based on `com.intellij.compiler.server.EelBuildCommandLineBuilder.syncProjectSpecificPathWithTarget`
        val remoteExternalProjectConfig = CompileServerLauncher.transferredRemotePath(
          path = ProjectUtil.getExternalConfigurationDir(project),
          project,
          eelDescriptor = EelProviderUtil.getEelDescriptor(project)
        )
        Some(remoteExternalProjectConfig)
      } else None

    val moduleNames = modules.map(_.getName)

    val command = CompileServerCommand.CompileJps(
      projectPath = projectPath,
      globalOptionsPath = globalOptionsPath,
      dataStorageRootPath = dataStorageRootPath,
      moduleNames = moduleNames.toSeq,
      sourceScope = sourceScope,
      projectMetadata = ProjectMetadataUtil.jpsProjectMetadata(project),
      externalProjectConfig = externalConfigurationDir
    )

    CompileServerClient.get(project).execCommand(command, client)
  }
}
