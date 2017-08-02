package org.jetbrains.plugins.cbt.runner

trait CbtProcessListener {
  def onTextAvailable(text: String, stderr: Boolean): Unit
  def onComplete(): Unit
}
