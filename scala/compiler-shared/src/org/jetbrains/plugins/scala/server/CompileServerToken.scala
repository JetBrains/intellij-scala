package org.jetbrains.plugins.scala.server

import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Path}
import java.util.UUID
import scala.util.control.NonFatal

object CompileServerToken {

  final val Tokens = "tokens"

  def tokenPathForPort(scalaCompileServerSystemDir: Path, port: Int): Path =
    scalaCompileServerSystemDir
      .resolve(Tokens)
      .resolve(port.toString)

  def tokenForPort(scalaCompileServerSystemDir: Path, port: Int): Option[String] =
     readStringFrom(tokenPathForPort(scalaCompileServerSystemDir, port))

  private def readStringFrom(path: Path): Option[String] =
    if (Files.exists(path))
      Some(new String(Files.readAllBytes(path), StandardCharsets.UTF_8))
    else None

  def generateAndWriteTokenFor(scalaCompileServerSystemDir: Path, port: Int): Path = {
    val path = tokenPathForPort(scalaCompileServerSystemDir, port)
    writeTokenTo(path, UUID.randomUUID())
  }

  private def writeTokenTo(path: Path, uuid: UUID): Path =
    FilePermissionsUtil.writeContentsToFileWithOwnerOnlyPermissions(path, uuid.toString)

  def removeTokenFileForPort(scalaCompileServerSystemDir: Path, port: Int): Unit = {
    val path = tokenPathForPort(scalaCompileServerSystemDir, port)
    try Files.deleteIfExists(path)
    catch { case NonFatal(_) => }
  }
}
