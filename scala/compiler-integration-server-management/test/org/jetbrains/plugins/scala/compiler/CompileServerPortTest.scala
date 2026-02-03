package org.jetbrains.plugins.scala.compiler

import org.jetbrains.plugins.scala.extensions.PathExt
import org.jetbrains.plugins.scala.server.CompileServerPort
import org.junit.Assert.assertEquals
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import org.junit.{Rule, Test}

import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Path}
import scala.annotation.meta.getter

@RunWith(classOf[Parameterized])
class CompileServerPortTest(port: Int) {

  @(Rule @getter)
  val temporaryDirectory: TemporaryFolder = new TemporaryFolder()

  @Test
  def portFilePath(): Unit = {
    val systemDir = temporaryScalaCompileServerSystemDir
    val expected = systemDir / CompileServerPort.PortFileName
    val actual = CompileServerPort.portFilePath(systemDir)
    assertEquals(expected, actual)
  }

  @Test
  def portNumber(): Unit = {
    val systemDir = temporaryScalaCompileServerSystemDir
    Files.createDirectories(systemDir)
    val portFilePath = systemDir / CompileServerPort.PortFileName
    import java.nio.file.StandardOpenOption.{CREATE, TRUNCATE_EXISTING}
    Files.writeString(portFilePath, port.toString, StandardCharsets.UTF_8, TRUNCATE_EXISTING, CREATE)
    val actual = CompileServerPort.readPortFile(systemDir)
    assertEquals(Some(port), actual)
  }

  private def temporaryScalaCompileServerSystemDir: Path =
    temporaryDirectory.newFolder("compile-server-port-test", "system", "scala-compile-server").toPath
}

private object CompileServerPortTest {
  @Parameterized.Parameters(name = "port = {0}")
  def parameters: java.util.Collection[Int] =
    java.util.List.of(3200, 6400, 10501, 50005, 55055)
}
