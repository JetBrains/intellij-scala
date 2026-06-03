package org.jetbrains.plugins.scala.compiler

import org.jetbrains.plugins.scala.extensions.PathExt
import org.jetbrains.plugins.scala.server.CompileServerPort
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource

import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Path}

class CompileServerPortTest:

  private def systemDir(temporaryDirectory: Path): Path =
    temporaryDirectory / "compile-server-port-test" / "system" / "scala-compile-server"

  @Test
  def portFilePath(@TempDir temporaryDirectory: Path): Unit =
    val sysDir = systemDir(temporaryDirectory)
    val expected = sysDir / CompileServerPort.PortFileName
    val actual = CompileServerPort.portFilePath(sysDir)
    assertEquals(expected, actual)

  @ParameterizedTest(name = "port = {0}")
  @ValueSource(ints = Array(3200, 6400, 10501, 50005, 55055))
  def portNumber(port: Int, @TempDir temporaryDirectory: Path): Unit =
    val sysDir = systemDir(temporaryDirectory)
    Files.createDirectories(sysDir)
    val portFilePath = sysDir / CompileServerPort.PortFileName
    import java.nio.file.StandardOpenOption.{CREATE, TRUNCATE_EXISTING}
    Files.writeString(portFilePath, port.toString, StandardCharsets.UTF_8, TRUNCATE_EXISTING, CREATE)
    val actual = CompileServerPort.readPortFile(sysDir)
    assertEquals(Some(port), actual)
