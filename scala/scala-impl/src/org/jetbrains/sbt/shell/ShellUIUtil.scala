package org.jetbrains.sbt.shell

import com.intellij.openapi.util.Computable
import com.intellij.util.ui.UIUtil

/**
  * Created by jast on 2017-02-16.
  */
object ShellUIUtil {
  def inUI(f: =>Unit): Unit = {
    UIUtil.invokeLaterIfNeeded(() => f)
  }
  def inUIsync[T](f: =>T): T = {
    UIUtil.invokeAndWaitIfNeeded(() => f)
  }
}
