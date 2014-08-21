package org.jetbrains.plugins.scala
package configuration

import com.intellij.openapi.vfs.{JarFileSystem, VirtualFile}

/**
 * @author Pavel Fatin
 */
package object template {
  def allFilesWithin(roots: Seq[VirtualFile]): Seq[VirtualFile] = allFilesWithin0(roots.toStream)

  private def allFilesWithin0(roots: Stream[VirtualFile]): Stream[VirtualFile] = {
    val (directories, files) = roots.span(_.isDirectory)
    files #::: directories.flatMap(it => allFilesWithin0(it.getChildren.toStream))
  }

  def toJarFile(file: VirtualFile): VirtualFile = {
    val path = file.getPath
    JarFileSystem.getInstance.findFileByPath(path + JarFileSystem.JAR_SEPARATOR)
  }
}
