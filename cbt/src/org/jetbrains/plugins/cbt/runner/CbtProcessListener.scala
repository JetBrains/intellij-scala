package org.jetbrains.plugins.cbt.runner

trait CbtProcessListener {
  def onTextAvailable(text: String, stderr: Boolean): Unit

  def onComplete(exitCode: Int): Unit
}

object CbtProcessListener {
  val Dummy: CbtProcessListener =
    new CbtProcessListener {
      override def onComplete(exitCode: Int): Unit = ()

      override def onTextAvailable(text: String, stderr: Boolean): Unit = ()
    }
}
