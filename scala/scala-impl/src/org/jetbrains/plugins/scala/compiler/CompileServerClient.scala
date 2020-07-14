package org.jetbrains.plugins.scala.compiler

import com.intellij.openapi.components.ServiceManager
import com.intellij.openapi.project.Project
import org.jetbrains.jps.incremental.scala.remote.{CompileServerCommand, CompileServerMeteringInfo}
import org.jetbrains.jps.incremental.scala.{Client, DummyClient}

import scala.concurrent.duration.FiniteDuration

trait CompileServerClient {

  def execCommand(command: CompileServerCommand, client: Client): Unit

  final def withMetering[A](meteringInterval: FiniteDuration)
                           (action: => Unit): CompileServerMeteringInfo = {
    val startCommand = CompileServerCommand.StartMetering(meteringInterval)
    val endCommand = CompileServerCommand.EndMetering()

    execCommand(startCommand, new DummyClient)
    action
    var result: CompileServerMeteringInfo = null
    execCommand(endCommand, new DummyClient {
      override def meteringInfo(info: CompileServerMeteringInfo): Unit = result = info
      override def trace(exception: Throwable): Unit = throw exception
    })
    result
  }
}

object CompileServerClient {

  def get(project: Project): CompileServerClient =
    ServiceManager.getService(project, classOf[CompileServerClient])
}

class CompileServerClientImpl(project: Project)
  extends CompileServerClient {

  override def execCommand(command: CompileServerCommand, client: Client): Unit =
    new RemoteServerRunner(project)
      .buildProcess(command.id, command.asArgs, client)
      .runSync()
}
