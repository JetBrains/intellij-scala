package org.jetbrains.plugins.scala.util

import com.intellij.openapi.progress.{ProcessCanceledException, ProgressIndicator, Task}
import com.intellij.ui.AnimatedIcon
import com.intellij.ui.components.JBLabel

import scala.util.Try
import scala.concurrent.Promise
import scala.concurrent.duration.FiniteDuration

object AsynchronousDownloadingUtil {

  def downloadVersionsTask[T](title: String, promise: Promise[T], iconLabel: JBLabel, timeout: FiniteDuration)(task: => T): Task.Backgroundable = {
    new Task.Backgroundable(null, title, false) {
      override def run(indicator: ProgressIndicator): Unit = {
        iconLabel.setVisible(true)
        promise.tryComplete(Try(task))
        iconLabel.setVisible(false)
      }

      override def onCancel(): Unit = {
        promise.tryFailure(new ProcessCanceledException())
      }
    }
  }

  def createLabelWithLoadingIcon(toolTipText: String): JBLabel = {
    val label = new JBLabel(AnimatedIcon.Default.INSTANCE)
    label.setVisible(false)
    label.setToolTipText(toolTipText)
    label
  }


}
