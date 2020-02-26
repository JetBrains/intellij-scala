package org.jetbrains.plugins.scala.externalHighlighters.compiler

import com.intellij.openapi.application.PathManager
import com.intellij.openapi.project.Project
import org.jetbrains.jps.incremental.scala.remote.{Commands, CompileServerArgs}
import org.jetbrains.plugins.scala.compiler.RemoteServerRunner

trait JpsCompiler {

  def compile(project: Project): Unit
}

class JpsCompilerImpl
  extends JpsCompiler {

  override def compile(project: Project): Unit = {
    val command = Commands.CompileJps
    val args = CompileServerArgs.CompileJps(
      token = "",
      projectPath = project.getBasePath,
      globalOptionsPath = PathManager.getOptionsPath
    ).asStringsWithoutToken
    val client = new CompilationClient(project)
    new RemoteServerRunner(project).buildProcess(command, args, client).runSync()
  }
}


