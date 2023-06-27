package org.jetbrains.plugins.scala.compiler

import com.intellij.compiler.server.CustomBuilderMessageHandler
import com.intellij.openapi.project.Project
import org.jetbrains.jps.incremental.messages.CustomBuilderMessage
import org.jetbrains.jps.incremental.scala.Client.ClientMsg
import org.jetbrains.jps.incremental.scala.MessageKind
import org.jetbrains.plugins.scala.util.ObjectSerialization

import scala.util.Try

private class CompilerEventFromCustomBuilderMessageListener(project: Project)
  extends CustomBuilderMessageHandler {

  override def messageReceived(builderId: String,
                               messageType: String,
                               messageText: String): Unit =
    fromCustomMessage(new CustomBuilderMessage(builderId, messageType, messageText))
      .foreach {
        case event @ CompilerEvent.MessageEmitted(_, _, Some(uuid), ClientMsg(kind, _, _, _, _, _)) =>
          kind match {
            case MessageKind.Error | MessageKind.InternalBuilderError =>
              JpsSessionErrorTrackerService.instance(project).register(uuid)
            case _ =>
          }
          project.getMessageBus.syncPublisher(CompilerEventListener.topic).eventReceived(event)
        case event =>
          project.getMessageBus.syncPublisher(CompilerEventListener.topic).eventReceived(event)
      }

  // Duplicated in org.jetbrains.jps.incremental.scala.remote.Jps to avoid complex compile time dependencies
  // between modules.
  private def fromCustomMessage(customMessage: CustomBuilderMessage): Option[CompilerEvent] = {
    val text = customMessage.getMessageText
    Option(customMessage)
      .filter(_.getBuilderId == CompilerEvent.BuilderId)
      .flatMap { msg => Try(CompilerEventType.withName(msg.getMessageType)).toOption }
      .map { _ => ObjectSerialization.fromBase64[CompilerEvent](text) }
  }
}
