package org.jetbrains.plugins.scala.compiler

import org.jetbrains.plugins.scala.extensions.PathExt
import org.jetbrains.plugins.scala.server.CompileServerLog
import org.junit.Assert.assertEquals
import org.junit.rules.TemporaryFolder
import org.junit.{Rule, Test}

import java.nio.file.Path
import scala.annotation.meta.getter

class CompileServerLogTest {

  @(Rule @getter)
  val temporaryDirectory: TemporaryFolder = new TemporaryFolder()

  @Test
  def logFilePath(): Unit = {
    val logDir = temporaryLogDir
    val actual = CompileServerLog.logFilePath(logDir)
    assertEquals(CompileServerLog.LogFileName, actual.getFileName.toString)
    val expected = logDir / CompileServerLog.LogFileName
    assertEquals(expected, actual)
  }

  private def temporaryLogDir: Path =
    temporaryDirectory.newFolder("compile-server-log-test", "log-dir").toPath
}
