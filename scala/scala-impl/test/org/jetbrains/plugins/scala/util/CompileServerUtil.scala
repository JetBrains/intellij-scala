package org.jetbrains.plugins.scala.util

import scala.concurrent.duration.Duration

import org.jetbrains.plugins.scala.compiler.CompileServerLauncher
import org.junit.Assert

/**
  * Nikolay.Tropin
  * 20-Dec-17
  */
object CompileServerUtil {
  def stopAndWait(timeout: Duration): Unit = {
    val exited = CompileServerLauncher.stopAndWaitTermination(timeout.toMillis)
    Assert.assertTrue(s"Compile server process have not terminated after $timeout", exited)
  }
}
