package org.jetbrains.plugins.scala.settings

import com.intellij.util.messages.Topic

trait CompilerHighlightingListener {
  def compilerHighlightingChanged(enabled: Boolean): Unit
}

object CompilerHighlightingListener {
  val Topic = new Topic[CompilerHighlightingListener](
    "compiler-based highlighting setting",
    classOf[CompilerHighlightingListener])
}
