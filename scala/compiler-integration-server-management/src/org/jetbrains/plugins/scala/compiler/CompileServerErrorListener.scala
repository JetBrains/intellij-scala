package org.jetbrains.plugins.scala.compiler

import com.intellij.util.messages.Topic

trait CompileServerErrorListener {
  def onError(text: String): Unit
}

object CompileServerErrorListener {
  @com.intellij.util.messages.Topic.AppLevel
  val Topic: Topic[CompileServerErrorListener] =
    new Topic("Scala Compile Server errors text topic", classOf[CompileServerErrorListener])
}
