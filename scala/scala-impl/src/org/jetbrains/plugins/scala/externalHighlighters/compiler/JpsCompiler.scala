package org.jetbrains.plugins.scala.externalHighlighters.compiler

import com.intellij.openapi.application.PathManager
import com.intellij.openapi.project.Project
import org.jetbrains.jps.incremental.scala.remote.{CommandIds, CompileServerCommand}
import org.jetbrains.plugins.scala.compiler.RemoteServerRunner

trait JpsCompiler {

  def compile(project: Project): Unit
}

class JpsCompilerImpl
  extends JpsCompiler {

  override def compile(project: Project): Unit = {
    val command = CommandIds.CompileJps
    val args = CompileServerCommand.CompileJps(
      token = "",
      projectPath = project.getBasePath,
      globalOptionsPath = PathManager.getOptionsPath
    ).asArgsWithoutToken
    val client = new CompilationClient(project)
    new RemoteServerRunner(project).buildProcess(command, args, client).runSync()
  }
}


