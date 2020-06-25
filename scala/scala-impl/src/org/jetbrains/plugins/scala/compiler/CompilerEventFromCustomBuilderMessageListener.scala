package org.jetbrains.plugins.scala.compiler

import com.intellij.compiler.server.CustomBuilderMessageHandler
import com.intellij.openapi.project.Project
import org.jetbrains.jps.incremental.messages.CustomBuilderMessage

private class CompilerEventFromCustomBuilderMessageListener(project: Project)
  extends CustomBuilderMessageHandler {

  override def messageReceived(builderId: String,
                               messageType: String,
                               messageText: String): Unit =
    CompilerEvent.fromCustomMessage(new CustomBuilderMessage(builderId, messageType, messageText))
      .foreach { event =>
        project.getMessageBus.syncPublisher(CompilerEventListener.topic).eventReceived(event)
      }
}
