package org.jetbrains.plugins.scala.testingSupport.junit

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.client.ClientSystemInfo
import com.intellij.testFramework.common.ThreadLeakTracker
import org.jetbrains.plugins.scala.DependencyManagerBase.RichStr
import org.jetbrains.plugins.scala.base.libraryLoaders.{IvyManagedLoader, LibraryLoader}

abstract class ScalaJUnit4TestingTestCaseBase extends ScalaJUnitTestingTestCaseBase {

  override protected def setUp(): Unit = {
    super.setUp()

    //noinspection ApiStatus,UnstableApiUsage
    if (ClientSystemInfo.isWindows) {
      ThreadLeakTracker.longRunningThreadCreated(
        ApplicationManager.getApplication,
        "JavaProcessMonitor"
      )
    }
  }

  override protected def additionalLibraries: Seq[LibraryLoader] = Seq(
    IvyManagedLoader(("junit" % "junit" % "4.13.2").transitive())
  )
}
