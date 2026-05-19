package org.jetbrains.plugins.scala.base

import com.intellij.openapi.module.Module
import com.intellij.openapi.util.io.NioFiles
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.testFramework.PsiTestUtil

import java.nio.file.Path

object SourceRootTestUtil {
  def addSourceRoot(module: Module, path: Path): Unit = {
    val rootFile = LocalFileSystem.getInstance.refreshAndFindFileByNioFile(path)
    if (rootFile eq null) {
      throw new IllegalArgumentException(s"Cannot find source root path: $path")
    }
    NioFiles.createIfNotExists(path)
    PsiTestUtil.addSourceRoot(module, rootFile)
  }
}
