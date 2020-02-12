package org.jetbrains.plugins.scala.components.libextensions

import com.intellij.openapi.progress.ProgressIndicator
import org.apache.ivy.util.AbstractMessageLogger

class ProgressIndicatorLogger(private val indicator: ProgressIndicator) extends AbstractMessageLogger {
  override def doProgress(): Unit = indicator.checkCanceled()
  override def doEndProgress(msg: String): Unit = indicator.setText(msg)
  override def log(msg: String, level: Int): Unit = { doProgress(); indicator.setText(msg) }
  override def rawlog(msg: String, level: Int): Unit = ()
}
