package org.jetbrains.plugins.scala.compiler

import com.intellij.openapi.project.Project
import org.jetbrains.jps.incremental.scala.remote.CompileServerCommand
import org.jetbrains.jps.incremental.scala.Client

trait CompileServerClient {

  def execCommand(command: CompileServerCommand, client: Client): Unit
}

class CompileServerClientImpl(project: Project)
  extends CompileServerClient {

  override def execCommand(command: CompileServerCommand, client: Client): Unit =
    new RemoteServerRunner(project)
      .buildProcess(command.id, command.asArgsWithoutToken, client)
      .runSync()
}
