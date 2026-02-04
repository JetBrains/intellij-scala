package org.jetbrains.plugins.scala.compiler

import org.jetbrains.plugins.scala.extensions.PathExt
import org.jetbrains.plugins.scala.server.CompileServerToken
import org.junit.Assert.{assertEquals, assertFalse, assertTrue}
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import org.junit.{Rule, Test}

import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Path}
import scala.annotation.meta.getter
import scala.jdk.CollectionConverters.SetHasAsScala

@RunWith(classOf[Parameterized])
class CompileServerTokenTest(port: Int) {

  @(Rule @getter)
  val temporaryDirectory: TemporaryFolder = new TemporaryFolder()

  @Test
  def tokenPathForPort(): Unit = {
    val systemDir = temporaryScalaCompileServerSystemDir
    val expected = systemDir / CompileServerToken.Tokens / port.toString
    val actual = CompileServerToken.tokenPathForPort(systemDir, port)
    assertEquals(expected, actual)
  }

  @Test
  def tokenForPort(): Unit = {
    val systemDir = temporaryScalaCompileServerSystemDir
    val tokensDirectory = systemDir / CompileServerToken.Tokens
    Files.createDirectories(tokensDirectory)
    val tokenFilePath = tokensDirectory / port.toString
    val tokenString = "some string that might be a token"
    Files.writeString(tokenFilePath, tokenString, StandardCharsets.UTF_8)
    val actual = CompileServerToken.tokenForPort(systemDir, port)
    assertEquals(Some(tokenString), actual)
  }

  @Test
  def generateAndWriteTokenForPort(): Unit = {
    val systemDir = temporaryScalaCompileServerSystemDir
    val tokenFilePath = CompileServerToken.generateAndWriteTokenFor(systemDir, port)

    assertTrue("The token file was not created", tokenFilePath.exists)

    val isPosix = tokenFilePath.getFileSystem.supportedFileAttributeViews().contains("posix")
    if (isPosix) {
      import java.nio.file.attribute.PosixFilePermission.{OWNER_READ, OWNER_WRITE}
      val permissions = Files.getPosixFilePermissions(tokenFilePath).asScala.toSet
      assertEquals("The token file was created with wrong posix filesystem permissions", Set(OWNER_READ, OWNER_WRITE), permissions)
    } else {
      val file = tokenFilePath.toFile
      assertFalse("The token file on Windows must not be executable", file.canExecute)
      assertTrue("The token file on Windows must be readable", file.canRead)
      assertTrue("The token file on Windows must be writable", file.canWrite)
    }
  }

  @Test
  def removeTokenFileForPortIsIdempotent(): Unit = {
    val systemDir = temporaryScalaCompileServerSystemDir
    val tokenFilePath = CompileServerToken.generateAndWriteTokenFor(systemDir, port)
    assertTrue("The token file was not created", tokenFilePath.exists)

    for (_ <- 1 to 5) {
      CompileServerToken.removeTokenFileForPort(systemDir, port)
    }

    assertFalse("The token file should have been removed", tokenFilePath.exists)
  }

  private def temporaryScalaCompileServerSystemDir: Path =
    temporaryDirectory.newFolder("compile-server-token-test", "system", "scala-compile-server").toPath
}

private object CompileServerTokenTest {
  @Parameterized.Parameters(name = "port = {0}")
  def parameters: java.util.Collection[Int] =
    java.util.List.of(3200, 6400, 10501, 50005, 55055)
}
