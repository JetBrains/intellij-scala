package org.jetbrains.plugins.scala.settings

import com.intellij.util.messages.Topic

trait CompilerHighlightingListener {
  def compilerHighlightingScala2Changed(enabled: Boolean): Unit
  def compilerHighlightingScala3Changed(enabled: Boolean): Unit
}

object CompilerHighlightingListener {
  val Topic = new Topic[CompilerHighlightingListener](
    "compiler-based highlighting setting",
    classOf[CompilerHighlightingListener])
}
