package org.jetbrains.jps.incremental.scala.local

import org.jetbrains.jps.incremental.messages.CustomBuilderMessage
import org.jetbrains.plugins.scala.compiler.CompilerEvent
import org.jetbrains.plugins.scala.util.ObjectSerialization

/**
 * This class exists only to override the `toString` method of [[CustomBuilderMessage]], as these custom build messages
 * seem to be shown in the build log in Fleet. Without overriding `toString`, the messages are unreadable.
 */
private final class Base64BuilderMessage(event: CompilerEvent)
  extends CustomBuilderMessage(CompilerEvent.BuilderId, event.eventType.toString, ObjectSerialization.toBase64(event)) {
  override def toString: String = event.toString
}
