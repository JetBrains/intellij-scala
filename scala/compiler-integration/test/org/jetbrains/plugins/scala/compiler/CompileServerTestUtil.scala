package org.jetbrains.plugins.scala.compiler

import com.intellij.openapi.application.ApplicationManager
import com.intellij.testFramework.common.ThreadLeakTracker

object CompileServerTestUtil {
  def registerLongRunningThreads(): Unit = {
    //noinspection ApiStatus,UnstableApiUsage
    ThreadLeakTracker.longRunningThreadCreated(
      ApplicationManager.getApplication,
      "BaseDataReader: output stream of scalaCompileServer",
      "BaseDataReader: error stream of scalaCompileServer",
      "scalaCompileServer"
    )
  }
}
