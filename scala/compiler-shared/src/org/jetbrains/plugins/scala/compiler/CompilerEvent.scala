package org.jetbrains.plugins.scala.compiler

import java.io.File

import org.jetbrains.jps.incremental.messages.CustomBuilderMessage
import org.jetbrains.jps.incremental.scala.Client
import org.jetbrains.plugins.scala.compiler.CompilerEventType.CompilerEventType
import org.jetbrains.plugins.scala.util.{CompilationId, ObjectSerialization}

import scala.util.Try

sealed trait CompilerEvent {

  def eventType: CompilerEventType

  def compilationId: CompilationId

  final def toCustomMessage: CustomBuilderMessage = new CustomBuilderMessage(
    CompilerEvent.BuilderId,
    eventType.toString,
    ObjectSerialization.toBase64(this)
  )
}

object CompilerEvent {

  // can be sent multiple times for different modules by jps compiler
  case class CompilationStarted(override val compilationId: CompilationId)
    extends CompilerEvent {
    
    override def eventType: CompilerEventType = CompilerEventType.CompilationStarted
  }
  
  case class MessageEmitted(override val compilationId: CompilationId, msg: Client.ClientMsg)
    extends CompilerEvent {

    override def eventType: CompilerEventType = CompilerEventType.MessageEmitted
  }

  case class ProgressEmitted(override val compilationId: CompilationId, progress: Double)
    extends CompilerEvent {

    override def eventType: CompilerEventType = CompilerEventType.ProgressEmitted
  }
  
  // can be sent multiple times for different modules by jps compiler
  case class CompilationFinished(override val compilationId: CompilationId, sources: Set[File])
    extends CompilerEvent {

    override def eventType: CompilerEventType = CompilerEventType.CompilationFinished
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
