package org.jetbrains.plugins.scala.build

import java.io.File

import com.intellij.build.FilePosition
import com.intellij.build.events.EventResult
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.openapi.project.Project
import org.jetbrains.jps.incremental.messages.BuildMessage.Kind
import org.jetbrains.jps.incremental.scala.Client
import org.jetbrains.jps.incremental.scala.Client.PosInfo
import org.jetbrains.plugins.scala.compiler.{CompilerEvent, CompilerEventListener}
import org.jetbrains.plugins.scala.util.CompilationId

import scala.collection.mutable

class CompilerEventReporter(project: Project, compilationId: CompilationId) extends BuildReporter {
  
  private val publisher = project.getMessageBus
    .syncPublisher(CompilerEventListener.topic)

  private val files = mutable.Set[File]()

  private def publish(kind: Kind, text: String, position: Option[FilePosition]) =
    position.foreach { pos =>
      val from = PosInfo(
        line = Some(pos.getStartLine),
        column = Some(pos.getStartColumn),
        offset = None
      )
      val to = PosInfo(
        line = Some(pos.getEndLine.toLong),
        column = Some(pos.getEndColumn.toLong),
        offset = None
      )
      val msg = Client.ClientMsg(kind, text, Some(pos.getFile), from, to)
      val event = CompilerEvent.MessageEmitted(compilationId, msg)
      files.add(pos.getFile)
      publisher.eventReceived(event)
    }

  private def finishFiles(): Unit = {
    val event = CompilerEvent.CompilationFinished(compilationId, files.toSet)
    publisher.eventReceived(event)
  }

  /** Clear any messages associated with file. */
  override def clear(file: File): Unit = {
    files.add(file)
    val event = CompilerEvent.CompilationFinished(compilationId, Set(file))
    publisher.eventReceived(event)
  }


  override def start(): Unit = {
    val event = CompilerEvent.CompilationStarted(compilationId)
    publisher.eventReceived(event)
  }
  
  override def finish(messages: BuildMessages): Unit = finishFiles()
  override def finishWithFailure(err: Throwable): Unit = finishFiles()
  override def finishCanceled(): Unit = finishFiles()

  override def warning(message: String, position: Option[FilePosition]): Unit =
    publish(Kind.WARNING, message, position)

  override def error(message: String, position: Option[FilePosition]): Unit =
    publish(Kind.ERROR, message, position)

  override def info(message: String, position: Option[FilePosition]): Unit =
    publish(Kind.INFO, message, position)

  override def log(message: String): Unit = ()
  override def startTask(eventId: BuildMessages.EventId, parent: Option[BuildMessages.EventId], message: String, time: Long): Unit = ()
  override def progressTask(eventId: BuildMessages.EventId, total: Long, progress: Long, unit: String, message: String, time: Long): Unit = ()
  override def finishTask(eventId: BuildMessages.EventId, message: String, result: EventResult, time: Long): Unit = ()

}
