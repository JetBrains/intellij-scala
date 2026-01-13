package org.jetbrains.plugins.scala.compiler.highlighting

import com.intellij.compiler.server.BuildManager
import com.intellij.openapi.application.PathManager
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.{Project, ProjectUtil, ProjectUtilCore}
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.util.io.PathKt
import org.jetbrains.jps.incremental.Utils
import org.jetbrains.jps.incremental.scala.Client
import org.jetbrains.jps.incremental.scala.remote.{CompileServerCommand, SourceScope}
import org.jetbrains.plugins.scala.compiler.CompileServerClient
import org.jetbrains.plugins.scala.project.ProjectExt

import java.nio.file.Path

private object IncrementalCompiler {

  def compile(project: Project, modules: Set[Module], sourceScope: SourceScope, client: Client): Unit = {
    val projectPath = Option(project.getPresentableUrl)
      .map(VirtualFileManager.extractPath)
      .getOrElse(throw new IllegalStateException("Can't determine project path"))
    val globalOptionsPath = PathManager.getOptionsPath
    val rootPath = Path.of(PathKt.getSystemIndependentPath(BuildManager.getInstance.getBuildSystemDirectory(project)))
    val dataStorageRootPath = Utils.getDataStorageRoot(rootPath.toFile, projectPath).getCanonicalPath

    /** @see `org.jetbrains.jps.incremental.scala.remote.Main.withModifiedExternalProjectPath` */
    val externalConfigurationDir =
      if (ProjectUtilCore.isExternalStorageEnabled(project)) Some(ProjectUtil.getExternalConfigurationDir(project).toString)
      else None

    val moduleNames = modules.map(_.getName)

    val command = CompileServerCommand.CompileJps(
      projectPath = projectPath,
      globalOptionsPath = globalOptionsPath,
      dataStorageRootPath = dataStorageRootPath,
      moduleNames = moduleNames.toSeq,
      sourceScope = sourceScope,
      projectMetadata = project.jpsProjectMetadata,
      externalProjectConfig = externalConfigurationDir
    )

    CompileServerClient.get(project).execCommand(command, client)
  }
}
