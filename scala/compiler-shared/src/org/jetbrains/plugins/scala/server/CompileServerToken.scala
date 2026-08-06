package org.jetbrains.plugins.scala.server

import java.nio.charset.StandardCharsets
import java.nio.file.attribute.PosixFilePermissions
import java.nio.file.{Files, Path}
import java.util.UUID
import scala.jdk.CollectionConverters.SetHasAsJava
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

  private def writeTokenTo(path: Path, uuid: UUID): Path = {
    val directory = path.getParent

    if (!Files.exists(directory)) {
      Files.createDirectories(directory)
    }

    val isPosix = path.getFileSystem.supportedFileAttributeViews().contains("posix")
    if (isPosix) {
      import java.nio.file.attribute.PosixFilePermission.{OWNER_READ, OWNER_WRITE}
      Files.createFile(path, PosixFilePermissions.asFileAttribute(Set(OWNER_READ, OWNER_WRITE).asJava))
    } else {
      // Windows
      // Using `java.io.File` here is unavoidable, as Windows is not compatible with `PosixFilePermission`
      // and throws exceptions at runtime.
      val file = path.toFile
      file.createNewFile()
      file.setReadable(/* readable */ true, /* ownerOnly */ true)
      file.setWritable(/* writable */ true, /* ownerOnly */ true)
    }

    import java.nio.file.StandardOpenOption.{TRUNCATE_EXISTING, CREATE}
    Files.write(path, uuid.toString.getBytes(StandardCharsets.UTF_8), TRUNCATE_EXISTING, CREATE)
  }

  def removeTokenFileForPort(scalaCompileServerSystemDir: Path, port: Int): Unit = {
    val path = tokenPathForPort(scalaCompileServerSystemDir, port)
    try Files.deleteIfExists(path)
    catch { case NonFatal(_) => }
  }
}
