package org.jetbrains.plugins.scala.compiler.highlighting

private trait Cancellable {
  def cancel(): Unit
}
