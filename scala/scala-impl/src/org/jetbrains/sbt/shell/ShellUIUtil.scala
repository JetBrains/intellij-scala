package org.jetbrains.sbt.shell

import com.intellij.openapi.util.Computable
import com.intellij.util.ui.UIUtil

/**
  * Created by jast on 2017-02-16.
  */
object ShellUIUtil {
  def inUI(f: =>Unit): Unit = {
    UIUtil.invokeLaterIfNeeded( new Runnable {
      override def run(): Unit = f
    })
  }
  def inUIsync[T](f: =>T): T = {
    UIUtil.invokeAndWaitIfNeeded( new Computable[T] {
      override def compute(): T = f
    })
  }
}
