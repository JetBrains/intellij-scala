package org.jetbrains.plugins.scala.compiler.testUtils

import org.jetbrains.plugins.scala.compiler.CompileServerLauncher

import scala.concurrent.duration.{Duration, FiniteDuration}

class ScalaCompileServerTester(
  reuseCompileServerProcessBetweenTests: Boolean,
  compileServerShutdownTimeout: Duration
) {
  def setUp(): Unit = {
    if (reuseCompileServerProcessBetweenTests) {
      CompileServerTestUtil.registerLongRunningThreads()
    } else {
      // We don't want to reuse the compile server in this test class, but it may have already been started.
      // We should shut it down first.
      stopCompileServer()
    }
  }

  def tearDown(): Unit = {
    if (!reuseCompileServerProcessBetweenTests) {
      stopCompileServer()
    } else {
      //  server will be stopped when Application shuts down (see ShutDownTracker in CompileServerLauncher)
    }
  }

  private def stopCompileServer(): Unit = {
    compileServerShutdownTimeout match {
      case _: Duration.Infinite =>
        CompileServerLauncher.stopServerAndWait()
      case duration: FiniteDuration =>
        CompileServerLauncher.stopServerAndWaitFor(duration)
    }
  }
}
