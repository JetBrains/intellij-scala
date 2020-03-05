package org.jetbrains.plugins.scala.compiler

import com.intellij.openapi.project.Project
import org.jetbrains.jps.incremental.scala.remote.CompileServerCommand
import org.jetbrains.jps.incremental.scala.{Client, DummyClient}

import scala.concurrent.duration.Duration
import scala.concurrent.{Await, Promise}

trait CompileServerClient {

  def execCommand(command: CompileServerCommand, client: Client): Unit

  final def getState(command: CompileServerCommand.GetState): Client.CompileServerState = {
    val promise = Promise[Client.CompileServerState]
    val client = new DummyClient {
      override def compileServerState(state: Client.CompileServerState): Unit = promise.success(state)
      override def trace(exception: Throwable): Unit = promise.failure(exception)
    }
    execCommand(command, client)
    Await.result(promise.future, Duration.Inf)
  }
}

class CompileServerClientImpl(project: Project)
  extends CompileServerClient {

  override def execCommand(command: CompileServerCommand, client: Client): Unit =
    new RemoteServerRunner(project)
      .buildProcess(command.id, command.asArgsWithoutToken, client)
      .runSync()
}
