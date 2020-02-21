package org.jetbrains.plugins.scala.build

import java.io.File

import com.intellij.build.FilePosition
import com.intellij.build.events.EventResult
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.openapi.project.Project
import org.jetbrains.plugins.scala.compiler.{CompilerEvent, CompilerEventListener}
import org.jetbrains.plugins.scala.util.CompilationId

import scala.collection.mutable

class CompilerEventReporter(project: Project, compilationId: CompilationId) extends BuildReporter {

  private val publisher = project.getMessageBus
    .syncPublisher(CompilerEventListener.topic)

  private val files = mutable.Set[File]()

  private def publish(severity: HighlightSeverity, text: String, position: Option[FilePosition]) = {
    position.map { pos =>
      val l0 = pos.getStartLine
      val c0 = pos.getStartColumn
      // if start and end position are the same, we assume actual range is not known
      val (l1,c1) =
        if (pos.getEndLine == l0 && pos.getEndColumn == c0)
          (None, None)
        else
          (Some(pos.getEndLine), Some(pos.getEndColumn))
      val msg = CompilerEvent.RangeMessage(severity, text, pos.getFile, l0, c0, l1, c1)
      val event = CompilerEvent.RangeMessageEmitted(compilationId, msg)
      files.add(pos.getFile)
      publisher.eventReceived(event)
    }
  }

  private def finishFiles(): Unit = files.foreach { file =>
    val event = CompilerEvent.CompilationFinished(compilationId, file)
    publisher.eventReceived(event)
  }


  override def finish(messages: BuildMessages): Unit = finishFiles()
  override def finishWithFailure(err: Throwable): Unit = finishFiles()
  override def finishCanceled(): Unit = finishFiles()

  override def warning(message: String, position: Option[FilePosition]): Unit =
    publish(HighlightSeverity.WARNING, message, position)

  override def error(message: String, position: Option[FilePosition]): Unit =
    publish(HighlightSeverity.ERROR, message, position)

  override def info(message: String, position: Option[FilePosition]): Unit =
    publish(HighlightSeverity.WEAK_WARNING, message, position)

  override def start(): Unit = ()
  override def log(message: String): Unit = ()
  override def startTask(eventId: BuildMessages.EventId, parent: Option[BuildMessages.EventId], message: String, time: CompilationId): Unit = ()
  override def progressTask(eventId: BuildMessages.EventId, total: CompilationId, progress: CompilationId, unit: String, message: String, time: CompilationId): Unit = ()
  override def finishTask(eventId: BuildMessages.EventId, message: String, result: EventResult, time: CompilationId): Unit = ()
}
