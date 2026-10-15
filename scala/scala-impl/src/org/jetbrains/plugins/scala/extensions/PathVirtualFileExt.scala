package org.jetbrains.plugins.scala.extensions

import com.intellij.openapi.vfs.{VirtualFile, VirtualFileManager}

import java.nio.file.Path

implicit class PathVirtualFileExt(private val path: Path) extends AnyVal {
  def toVirtualFile: Option[VirtualFile] =
    VirtualFileManager.getInstance().findFileByNioPath(path).toOption
}
