package org.jetbrains.plugins.scala.compiler

import java.util.EventListener

import com.intellij.util.messages.Topic

trait CompilerEventListener
  extends EventListener {

  def eventReceived(event: CompilerEvent): Unit
}

object CompilerEventListener {

  final val topic = Topic.create(
    "compiler events",
    classOf[CompilerEventListener]
  )
}
