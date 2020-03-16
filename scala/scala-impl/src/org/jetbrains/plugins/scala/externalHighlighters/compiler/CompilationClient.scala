package org.jetbrains.plugins.scala.externalHighlighters.compiler

import java.io.File

import com.intellij.openapi.project.Project
import org.jetbrains.jps.incremental.scala.{Client, DummyClient}
import org.jetbrains.plugins.scala.compiler.{CompilerEvent, CompilerEventListener}
import org.jetbrains.plugins.scala.util.CompilationId

class CompilationClient(project: Project)
  extends DummyClient {

  final val compilationId = CompilationId.generate()

  override def message(msg: Client.ClientMsg): Unit =
    sendEvent(CompilerEvent.MessageEmitted(compilationId, msg))

  override def compilationEnd(sources: Set[File]): Unit =
    sources.foreach { source =>
      sendEvent(CompilerEvent.CompilationFinished(compilationId, source))
    }

  def sendEvent(event: CompilerEvent): Unit =
    project.getMessageBus
      .syncPublisher(CompilerEventListener.topic)
      .eventReceived(event)
}

