package org.jetbrains.plugins.scala.compiler

import java.io.File

import com.intellij.lang.annotation.HighlightSeverity
import org.jetbrains.jps.incremental.messages.CustomBuilderMessage
import org.jetbrains.jps.incremental.scala.Client
import org.jetbrains.plugins.scala.compiler.CompilerEventType.CompilerEventType
import org.jetbrains.plugins.scala.util.{CompilationId, ObjectSerialization}

import scala.util.Try

sealed trait CompilerEvent {

  def eventType: CompilerEventType

  def compilationId: CompilationId

  def toCustomMessage: CustomBuilderMessage = new CustomBuilderMessage(
    CompilerEvent.BuilderId,
    eventType.toString,
    ObjectSerialization.toBase64(this)
  )
}

object CompilerEvent {

  // TODO merge MessageEmitted and RangeMessageEmitted into one class
  case class MessageEmitted(override val compilationId: CompilationId, msg: Client.ClientMsg)
    extends CompilerEvent {

    override def eventType: CompilerEventType = CompilerEventType.MessageEmitted
  }

  final case class RangeMessageEmitted(override val compilationId: CompilationId, msg: RangeMessage)
    extends CompilerEvent {

    override def eventType: CompilerEventType = CompilerEventType.RangeMessageEmitted
  }

  final case class RangeMessage(
    severity: HighlightSeverity, text: String, source: File,
    fromLine: Int, fromColumn: Int,
    toLine: Option[Int], toColumn: Option[Int])

  // can be sent multiple times for different modules by jps compiler
  case class CompilationFinished(override val compilationId: CompilationId, sources: Set[File])
    extends CompilerEvent {

    override def eventType: CompilerEventType = CompilerEventType.CompilationFinished
  }

  case class ProgressEmitted(override val compilationId: CompilationId, progress: Double)
    extends CompilerEvent {

    override def eventType: CompilerEventType = CompilerEventType.ProgressEmitted
  }

  def fromCustomMessage(customMessage: CustomBuilderMessage): Option[CompilerEvent] = {
    val text = customMessage.getMessageText
    Option(customMessage)
      .filter(_.getBuilderId == BuilderId)
      .flatMap { msg => Try(CompilerEventType.withName(msg.getMessageType)).toOption }
      .map { _ => ObjectSerialization.fromBase64[CompilerEvent](text) }
  }

  val BuilderId = "compiler-event"
}
