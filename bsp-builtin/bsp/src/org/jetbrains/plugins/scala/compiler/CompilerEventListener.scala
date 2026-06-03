package org.jetbrains.plugins.scala.compiler

import com.intellij.util.messages.Topic

import java.util.EventListener

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
