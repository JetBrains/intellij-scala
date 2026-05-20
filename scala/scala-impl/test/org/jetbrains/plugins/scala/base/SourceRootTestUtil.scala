package org.jetbrains.plugins.scala.base

import com.intellij.openapi.module.Module
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.testFramework.PsiTestUtil

import java.nio.file.{Files, Path}

object SourceRootTestUtil {
  def addSourceRoot(module: Module, path: Path): Unit = {
    val rootFile = LocalFileSystem.getInstance.refreshAndFindFileByNioFile(path)
    if (rootFile eq null) {
      throw new IllegalArgumentException(s"Cannot find source root path: $path")
    }
    createIfNotExists(path)
    PsiTestUtil.addSourceRoot(module, rootFile)
  }

  private def createIfNotExists(path: Path): Unit = {
    if (Files.exists(path)) return
    if (Files.isDirectory(path)) Files.createDirectories(path)
    else {
      Files.createDirectories(path.getParent)
      Files.createFile(path)
    }
  }
}
