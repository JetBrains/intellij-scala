package org.jetbrains.plugins.scala.compiler

import org.jetbrains.plugins.scala.extensions.PathExt
import org.jetbrains.plugins.scala.server.CompileServerLog
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

import java.nio.file.Path

class CompileServerLogTest:

  @Test
  def logFilePath(@TempDir temporaryDirectory: Path): Unit =
    val logDir = temporaryDirectory / "compile-server-log-test" / "log-dir"
    val actual = CompileServerLog.logFilePath(logDir)
    assertEquals(CompileServerLog.LogFileName, actual.getFileName.toString)
    val expected = logDir / CompileServerLog.LogFileName
    assertEquals(expected, actual)
