package org.jetbrains.plugins.scala.compiler.highlighting

import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.project.Project
import org.jetbrains.annotations.Nls
import org.jetbrains.jps.incremental.scala.{Client, DummyClient, MessageKind}
import org.jetbrains.plugins.scala.compiler.{CompilerEvent, CompilerEventListener, CompilerIntegrationBundle}
import org.jetbrains.plugins.scala.util.CompilationId

import java.io.File

private class CompilerEventGeneratingClient(
  project: Project,
  indicator: ProgressIndicator,
  log: Logger
) extends DummyClient {

  final val compilationId = CompilationId.generate()

  private var hasErrors: Boolean = false

  indicator.setIndeterminate(false)

  override def progress(@Nls text: String, done: Option[Float]): Unit = {
    indicator.setText(CompilerIntegrationBundle.message("highlighting.compilation.progress", text))
    indicator.setFraction(done.getOrElse(-1.0F).toDouble)
    done.foreach { doneVal =>
      sendEvent(CompilerEvent.ProgressEmitted(compilationId, None, doneVal))
    }
  }

  override def message(msg: Client.ClientMsg): Unit = {
    if (msg.text ne null) {
      // TODO: SCL-22142 Revisit after discussing the changes to fatal warnings in Scala 3.4.1 with the compiler team.
      val description = CompilerMessages.description(msg.text)
      if (CompilerMessages.isNoWarningsCanBeIncurred(description)) {
        return
      }
    }

    msg.kind match {
      case MessageKind.Error | MessageKind.InternalBuilderError => hasErrors = true
      case _ =>
    }
    sendEvent(CompilerEvent.MessageEmitted(compilationId, None, None, msg))
  }

  override def compilationStart(): Unit =
    sendEvent(CompilerEvent.CompilationStarted(compilationId, None))

  override def compilationEnd(sources: Set[File]): Unit =
    sendEvent(CompilerEvent.CompilationFinished(compilationId, None, sources))

  override def isCanceled: Boolean = indicator.isCanceled

  override def trace(exception: Throwable): Unit =
    log.error(s"[${project.getName}] ${exception.getMessage}", exception)

  def successful: Boolean = !hasErrors

  private def sendEvent(event: CompilerEvent): Unit =
    project.getMessageBus
      .syncPublisher(CompilerEventListener.topic)
      .eventReceived(event)
}