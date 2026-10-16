package org.jetbrains.plugins.scala.compiler

import org.junit.jupiter.api.Assertions.{assertEquals, assertTrue}

import java.nio.file.{Files, Path}
import scala.jdk.CollectionConverters.SetHasAsScala

private object FilePermissionsTestUtil:
  def isPosix(path: Path): Boolean =
    path.getFileSystem.supportedFileAttributeViews().contains("posix")

  def assertOwnerOnlyPermissions(path: Path, fileType: String): Unit =
    if isPosix(path) then
      import java.nio.file.attribute.PosixFilePermission.{OWNER_READ, OWNER_WRITE}
      val permissions = Files.getPosixFilePermissions(path).asScala.toSet
      assertEquals(Set(OWNER_READ, OWNER_WRITE), permissions, s"The $fileType file was created with wrong posix filesystem permissions")
    else
      assertTrue(Files.isReadable(path), s"The $fileType file on Windows must be readable")
      assertTrue(Files.isWritable(path), s"The $fileType file on Windows must be writable")
