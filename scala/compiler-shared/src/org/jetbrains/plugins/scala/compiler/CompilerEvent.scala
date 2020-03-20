package org.jetbrains.plugins.scala.compiler

import java.io.File

import com.intellij.lang.annotation.HighlightSeverity
import org.jetbrains.jps.incremental.messages.CustomBuilderMessage
import org.jetbrains.jps.incremental.scala.Client
import org.jetbrains.plugins.scala.util.{CompilationId, ObjectSerialization}

sealed trait CompilerEvent {

  def eventType: String

  def compilationId: CompilationId

  def toCustomMessage: CustomBuilderMessage = new CustomBuilderMessage(
    CompilerEvent.BuilderId,
    eventType,
    ObjectSerialization.toBase64(this)
  )
}

object CompilerEvent {

  @SerialVersionUID(-1920831455397960151L)
  case class MessageEmitted(override val compilationId: CompilationId, msg: Client.ClientMsg)
    extends CompilerEvent {

    override def eventType: String = MessageEmitted.EventType
  }

  object MessageEmitted {
    val EventType = "message-emitted"
  }


  final case class RangeMessageEmitted(override val compilationId: CompilationId, msg: RangeMessage)
    extends CompilerEvent {
    override def eventType: String = RangeMessageEmitted.EventType
  }
  object RangeMessageEmitted {
    val EventType = "range-message-emitted"
  }

  final case class RangeMessage(
    severity: HighlightSeverity, text: String, source: File,
    fromLine: Int, fromColumn: Int,
    toLine: Option[Int], toColumn: Option[Int])

  @SerialVersionUID(2805617802395239646L)
  case class CompilationFinished(override val compilationId: CompilationId, source: File)
    extends CompilerEvent {

    override def eventType: String = CompilationFinished.EventType
  }

  object CompilationFinished {
    val EventType = "compilation-finished"
  }

  def fromCustomMessage(customMessage: CustomBuilderMessage): Option[CompilerEvent] = {
    val text = customMessage.getMessageText
    Option(customMessage)
      .filter(_.getBuilderId == BuilderId)
      .map(_.getMessageType)
      .collect {
        case MessageEmitted.EventType | CompilationFinished.EventType | RangeMessageEmitted.EventType =>
          ObjectSerialization.fromBase64(text)
      }
  }

  val BuilderId = "compiler-event"
}
