package org.jetbrains.plugins.scala.build

import com.intellij.build.FilePosition
import com.intellij.build.events.EventResult
import com.intellij.openapi.project.Project
import org.jetbrains.annotations.Nls
import org.jetbrains.jps.incremental.scala.Client.PosInfo
import org.jetbrains.jps.incremental.scala.{Client, MessageKind}
import org.jetbrains.plugins.scala.compiler.{CompilerEvent, CompilerEventListener}
import org.jetbrains.plugins.scala.util.CompilationId

import java.io.File
import scala.collection.mutable

class CompilerEventReporter(project: Project,
                            compilationId: CompilationId)
  extends BuildReporter {
  
  private val publisher = project.getMessageBus
    .syncPublisher(CompilerEventListener.topic)

  private val files = mutable.Set[File]()

  private var hasErrors: Boolean = false

  private def publish(kind: MessageKind, @Nls text: String, position: Option[FilePosition]): Unit =
    position.foreach { pos =>
      // com.intellij.build.FilePosition contains 0-based line and column information, PosInfo expects 1-based indices.
      val problemStart = PosInfo(pos.getStartLine + 1, pos.getStartColumn + 1)
      val problemEnd = PosInfo(pos.getEndLine + 1, pos.getEndColumn + 1)
      val msg = Client.ClientMsg(kind, text, Some(pos.getFile), Some(problemStart), Some(problemStart), Some(problemEnd))
      val event = CompilerEvent.MessageEmitted(compilationId, None, None, msg)
      files.add(pos.getFile)
      publisher.eventReceived(event)
    }

  private def finishFiles(): Unit = {
    val event = CompilerEvent.CompilationFinished(compilationId, None, files.toSet)
    publisher.eventReceived(event)
  }

  /** Clear any messages associated with file. */
  override def clear(file: File): Unit = {
    files.add(file)
    val event = CompilerEvent.CompilationFinished(compilationId, None, Set(file))
    publisher.eventReceived(event)
  }


  override def start(): Unit = {
    val event = CompilerEvent.CompilationStarted(compilationId, None)
    publisher.eventReceived(event)
  }
  
  override def finish(messages: BuildMessages): Unit = finishFiles()
  override def finishWithFailure(err: Throwable): Unit = {
    finishFiles()
    hasErrors = true
  }
  override def finishCanceled(): Unit = {
    finishFiles()
    hasErrors = true
  }

  override def warning(@Nls message: String, position: Option[FilePosition]): Unit =
    publish(MessageKind.Warning, message, position)

  override def error(@Nls message: String, position: Option[FilePosition]): Unit = {
    publish(MessageKind.Error, message, position)
    hasErrors = true
  }

  override def info(@Nls message: String, position: Option[FilePosition]): Unit =
    publish(MessageKind.Info, message, position)

  override def log(message: String): Unit = ()
  override def startTask(eventId: BuildMessages.EventId, parent: Option[BuildMessages.EventId], message: String, time: Long): Unit = ()
  override def progressTask(eventId: BuildMessages.EventId, total: Long, progress: Long, unit: String, message: String, time: Long): Unit = ()
  override def finishTask(eventId: BuildMessages.EventId, message: String, result: EventResult, time: Long): Unit = ()

  private[scala] def successful: Boolean = !hasErrors
}
