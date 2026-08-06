package org.jetbrains.plugins.scala.compiler.references.compilation

import com.intellij.compiler.server.BuildManager
import com.intellij.openapi.application.ApplicationManager

private object BuildThreadUtil {
  def executeOnBuildThread(runnable: Runnable): Unit = {
    if (ApplicationManager.getApplication.isUnitTestMode) {
      runnable.run()
    } else {
      BuildManager.getInstance().runCommand(runnable)
    }
  }
}
