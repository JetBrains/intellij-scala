package org.jetbrains.plugins.scala.compiler

import org.jetbrains.plugins.scala.extensions.PathExt
import org.jetbrains.plugins.scala.server.CompileServerToken
import org.junit.jupiter.api.Assertions.{assertEquals, assertFalse, assertTrue}
import org.junit.jupiter.api.io.TempDir
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource

import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Path}
import java.util.stream.IntStream
import scala.jdk.CollectionConverters.SetHasAsScala

class CompileServerTokenTest:

  private def systemDir(temporaryDirectory: Path): Path =
    temporaryDirectory / "compile-server-token-test" / "system" / "scala-compile-server"

  @ParameterizedTest(name = "port = {0}")
  @MethodSource(Array("ports"))
  def tokenPathForPort(port: Int, @TempDir temporaryDirectory: Path): Unit =
    val sysDir = systemDir(temporaryDirectory)
    val expected = sysDir / CompileServerToken.Tokens / port.toString
    val actual = CompileServerToken.tokenPathForPort(sysDir, port)
    assertEquals(expected, actual)

  @ParameterizedTest(name = "port = {0}")
  @MethodSource(Array("ports"))
  def tokenForPort(port: Int, @TempDir temporaryDirectory: Path): Unit =
    val sysDir = systemDir(temporaryDirectory)
    val tokensDirectory = sysDir / CompileServerToken.Tokens
    Files.createDirectories(tokensDirectory)
    val tokenFilePath = tokensDirectory / port.toString
    val tokenString = "some string that might be a token"
    Files.writeString(tokenFilePath, tokenString, StandardCharsets.UTF_8)
    val actual = CompileServerToken.tokenForPort(sysDir, port)
    assertEquals(Some(tokenString), actual)

  @ParameterizedTest(name = "port = {0}")
  @MethodSource(Array("ports"))
  def generateAndWriteTokenForPort(port: Int, @TempDir temporaryDirectory: Path): Unit =
    val sysDir = systemDir(temporaryDirectory)
    val tokenFilePath = CompileServerToken.generateAndWriteTokenFor(sysDir, port)

    assertTrue(tokenFilePath.exists, "The token file was not created")

    val isPosix = tokenFilePath.getFileSystem.supportedFileAttributeViews().contains("posix")
    if isPosix then
      import java.nio.file.attribute.PosixFilePermission.{OWNER_READ, OWNER_WRITE}
      val permissions = Files.getPosixFilePermissions(tokenFilePath).asScala.toSet
      assertEquals(Set(OWNER_READ, OWNER_WRITE), permissions, "The token file was created with wrong posix filesystem permissions")
    else
      assertTrue(Files.isReadable(tokenFilePath), "The token file on Windows must be readable")
      assertTrue(Files.isWritable(tokenFilePath), "The token file on Windows must be writable")

  @ParameterizedTest(name = "port = {0}")
  @MethodSource(Array("ports"))
  def removeTokenFileForPortIsIdempotent(port: Int, @TempDir temporaryDirectory: Path): Unit =
    val sysDir = systemDir(temporaryDirectory)
    val tokenFilePath = CompileServerToken.generateAndWriteTokenFor(sysDir, port)
    assertTrue(tokenFilePath.exists, "The token file was not created")

    for _ <- 1 to 5 do
      CompileServerToken.removeTokenFileForPort(sysDir, port)

    assertFalse(tokenFilePath.exists, "The token file should have been removed")

private object CompileServerTokenTest:
  def ports(): IntStream = IntStream.of(3200, 6400, 10501, 50005, 55055)
