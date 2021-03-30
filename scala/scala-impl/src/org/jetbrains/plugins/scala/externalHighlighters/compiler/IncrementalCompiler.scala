package org.jetbrains.plugins.scala.externalHighlighters.compiler

import com.intellij.compiler.server.BuildManager
import com.intellij.openapi.application.PathManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.util.io.PathKt
import org.jetbrains.jps.incremental.Utils
import org.jetbrains.jps.incremental.scala.Client
import org.jetbrains.jps.incremental.scala.remote.CompileServerCommand
import org.jetbrains.plugins.scala.compiler.CompileServerClient

import java.io.File

object IncrementalCompiler {

  def compile(project: Project, client: Client): Unit = {
    val projectPath = Option(project.getPresentableUrl)
      .map(VirtualFileManager.extractPath)
      .getOrElse(throw new IllegalStateException("Can't determine project path"))
    val globalOptionsPath = PathManager.getOptionsPath
    val dataStorageRootPath = Utils.getDataStorageRoot(
      new File(PathKt.getSystemIndependentPath(BuildManager.getInstance.getBuildSystemDirectory(project))),
      projectPath
    ).getCanonicalPath
    val command = CompileServerCommand.CompileJps(
      projectPath = projectPath,
      globalOptionsPath = globalOptionsPath,
      dataStorageRootPath = dataStorageRootPath
    )

    CompileServerClient.get(project).execCommand(command, client)
  }
}
