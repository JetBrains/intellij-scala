package org.jetbrains.plugins.cbt.runner

trait CbtProcessListener { self =>
  def onTextAvailable(text: String, stderr: Boolean): Unit

  def onComplete(exitCode: Int): Unit

  def append(other: CbtProcessListener): CbtProcessListener =
    new CbtProcessListener {
      override def onComplete(exitCode: Int): Unit = {
        self.onComplete(exitCode)
        other.onComplete(exitCode)
      }

      override def onTextAvailable(text: String, stderr: Boolean): Unit = {
        self.onTextAvailable(text, stderr)
        other.onTextAvailable(text, stderr)
      }
    }
}

object CbtProcessListener {
  val Dummy: CbtProcessListener =
    new CbtProcessListener {
      override def onComplete(exitCode: Int): Unit = ()

      override def onTextAvailable(text: String, stderr: Boolean): Unit = ()
    }
}
