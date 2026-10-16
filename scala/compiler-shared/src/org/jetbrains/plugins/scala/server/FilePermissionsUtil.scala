package org.jetbrains.plugins.scala.server

import java.nio.charset.StandardCharsets
import java.nio.file.attribute.PosixFilePermissions
import java.nio.file.{Files, Path}
import scala.jdk.CollectionConverters.SetHasAsJava

object FilePermissionsUtil:
  def writeContentsToFileWithOwnerOnlyPermissions(path: Path, contents: String): Path =
    val directory = path.getParent

    if !Files.exists(directory) then
      Files.createDirectories(directory)

    val isPosix = path.getFileSystem.supportedFileAttributeViews().contains("posix")
    if isPosix then
      import java.nio.file.attribute.PosixFilePermission.{OWNER_READ, OWNER_WRITE}
      Files.createFile(path, PosixFilePermissions.asFileAttribute(Set(OWNER_READ, OWNER_WRITE).asJava))
    else
      // Windows
      // Using `java.io.File` here is unavoidable, as Windows is not compatible with `PosixFilePermission`
      // and throws exceptions at runtime.
      val file = path.toFile
      file.createNewFile()
      file.setReadable(/* readable */ true, /* ownerOnly */ true)
      file.setWritable(/* writable */ true, /* ownerOnly */ true)

    import java.nio.file.StandardOpenOption.{CREATE, TRUNCATE_EXISTING}
    Files.write(path, contents.getBytes(StandardCharsets.UTF_8), TRUNCATE_EXISTING, CREATE)
  end writeContentsToFileWithOwnerOnlyPermissions
