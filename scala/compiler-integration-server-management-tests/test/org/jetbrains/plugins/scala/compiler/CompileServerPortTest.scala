package org.jetbrains.plugins.scala.compiler

import org.jetbrains.plugins.scala.extensions.PathExt
import org.jetbrains.plugins.scala.server.CompileServerPort
import org.junit.jupiter.api.Assertions.{assertEquals, assertTrue}
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource

import java.nio.charset.StandardCharsets
import java.nio.file.attribute.PosixFilePermissions
import java.nio.file.{Files, Path}
import java.util.stream.IntStream
import scala.annotation.static
import scala.jdk.CollectionConverters.SetHasAsScala

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
  @MethodSource(Array("ports"))
  def portNumber(port: Int, @TempDir temporaryDirectory: Path): Unit =
    val sysDir = systemDir(temporaryDirectory)
    Files.createDirectories(sysDir)
    val portFilePath = sysDir / CompileServerPort.PortFileName
    import java.nio.file.StandardOpenOption.{CREATE, TRUNCATE_EXISTING}
    Files.writeString(portFilePath, port.toString, StandardCharsets.UTF_8, TRUNCATE_EXISTING, CREATE)
    val actual = CompileServerPort.readPortFile(sysDir)
    assertEquals(Some(port), actual)

  @ParameterizedTest(name = "port = {0}")
  @MethodSource(Array("ports"))
  def writePortFile(port: Int, @TempDir temporaryDirectory: Path): Unit =
    val sysDir = systemDir(temporaryDirectory)
    CompileServerPort.writePortFile(sysDir, port)
    val portFilePath = CompileServerPort.portFilePath(sysDir)

    assertTrue(portFilePath.exists, "The port file was not created")
    assertEquals(Some(port), CompileServerPort.readPortFile(sysDir))
    assertOwnerOnlyPermissions(portFilePath)

  @Test
  def writePortFileOverwritesExistingPortFile(@TempDir temporaryDirectory: Path): Unit =
    val sysDir = systemDir(temporaryDirectory)
    CompileServerPort.writePortFile(sysDir, 3200)
    CompileServerPort.writePortFile(sysDir, 6400)

    assertEquals(Some(6400), CompileServerPort.readPortFile(sysDir))
    assertOwnerOnlyPermissions(CompileServerPort.portFilePath(sysDir))

  @Test
  def writePortFileReplacesPortFileWithWiderPermissions(@TempDir temporaryDirectory: Path): Unit =
    val sysDir = systemDir(temporaryDirectory)
    Files.createDirectories(sysDir)
    val portFilePath = CompileServerPort.portFilePath(sysDir)
    Files.writeString(portFilePath, "3200", StandardCharsets.UTF_8)
    // Set the permissions explicitly, otherwise a restrictive umask could make this test pass vacuously
    if isPosix(portFilePath) then
      Files.setPosixFilePermissions(portFilePath, PosixFilePermissions.fromString("rw-r--r--"))

    CompileServerPort.writePortFile(sysDir, 6400)

    assertEquals(Some(6400), CompileServerPort.readPortFile(sysDir))
    assertOwnerOnlyPermissions(portFilePath)

  private def isPosix(path: Path): Boolean =
    path.getFileSystem.supportedFileAttributeViews().contains("posix")

  private def assertOwnerOnlyPermissions(path: Path): Unit =
    if isPosix(path) then
      import java.nio.file.attribute.PosixFilePermission.{OWNER_READ, OWNER_WRITE}
      val permissions = Files.getPosixFilePermissions(path).asScala.toSet
      assertEquals(Set(OWNER_READ, OWNER_WRITE), permissions, "The port file was created with wrong posix filesystem permissions")
    else
      assertTrue(Files.isReadable(path), "The port file on Windows must be readable")
      assertTrue(Files.isWritable(path), "The port file on Windows must be writable")

private object CompileServerPortTest:
  @static
  def ports(): IntStream = IntStream.of(3200, 6400, 10501, 50005, 55055)
