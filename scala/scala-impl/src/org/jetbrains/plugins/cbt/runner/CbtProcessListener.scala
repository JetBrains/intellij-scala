package org.jetbrains.plugins.cbt.runner

trait CbtProcessListener {
  def onTextAvailable(text: String, stderr: Boolean): Unit

  def onComplete(): Unit
}

object CbtProcessListener {
  val Dummy = new CbtProcessListener {
    override def onComplete(): Unit = ()

    override def onTextAvailable(text: String, stderr: Boolean): Unit = ()
  }
}
