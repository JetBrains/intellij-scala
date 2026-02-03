package org.jetbrains.plugins.scala.compiler

import org.jetbrains.plugins.scala.extensions.PathExt
import org.jetbrains.plugins.scala.server.CompileServerToken
import org.junit.Assert.assertEquals
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import org.junit.{Rule, Test}

import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Path}
import scala.annotation.meta.getter

@RunWith(classOf[Parameterized])
class CompileServerTokenTest(port: Int) {

  @(Rule @getter)
  val temporaryDirectory: TemporaryFolder = new TemporaryFolder()

  @Test
  def tokenPathForPort(): Unit = {
    val systemDir = temporaryScalaCompileServerSystemDir
    val expected = systemDir / "tokens" / port.toString
    val actual = CompileServerToken.tokenPathForPort(systemDir, port)
    assertEquals(expected, actual)
  }

  @Test
  def tokenForPort(): Unit = {
    val systemDir = temporaryScalaCompileServerSystemDir
    val tokensDirectory = systemDir / "tokens"
    Files.createDirectories(tokensDirectory)
    val tokenFilePath = tokensDirectory / port.toString
    val tokenString = "some string that might be a token"
    Files.writeString(tokenFilePath, tokenString, StandardCharsets.UTF_8)
    val actual = CompileServerToken.tokenForPort(systemDir, port)
    assertEquals(Some(tokenString), actual)
  }

  private def temporaryScalaCompileServerSystemDir: Path =
    temporaryDirectory.newFolder("compile-server-token-test", "system", "scala-compile-server").toPath
}

private object CompileServerTokenTest {
  @Parameterized.Parameters(name = "port = {0}")
  def parameters: java.util.Collection[Int] =
    java.util.List.of(3200, 6400, 10501, 50005, 55055)
}
