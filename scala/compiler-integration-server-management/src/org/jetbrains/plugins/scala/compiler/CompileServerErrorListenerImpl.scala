package org.jetbrains.plugins.scala.compiler

private final class CompileServerErrorListenerImpl extends CompileServerErrorListener {
  override def onError(text: String): Unit = {
    CompileServerErrorNotificationService.instance().onError(text)
  }
}
