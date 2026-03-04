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

class CompileServerPortTest {

  @TempDir
  var temporaryDirectory: Path = _

  private def systemDir: Path =
    temporaryDirectory / "compile-server-port-test" / "system" / "scala-compile-server"

  @Test
  def portFilePath(): Unit = {
    val expected = systemDir / CompileServerPort.PortFileName
    val actual = CompileServerPort.portFilePath(systemDir)
    assertEquals(expected, actual)
  }

  @ParameterizedTest(name = "port = {0}")
  @ValueSource(ints = Array(3200, 6400, 10501, 50005, 55055))
  def portNumber(port: Int): Unit = {
    Files.createDirectories(systemDir)
    val portFilePath = systemDir / CompileServerPort.PortFileName
    import java.nio.file.StandardOpenOption.{CREATE, TRUNCATE_EXISTING}
    Files.writeString(portFilePath, port.toString, StandardCharsets.UTF_8, TRUNCATE_EXISTING, CREATE)
    val actual = CompileServerPort.readPortFile(systemDir)
    assertEquals(Some(port), actual)
  }
}
