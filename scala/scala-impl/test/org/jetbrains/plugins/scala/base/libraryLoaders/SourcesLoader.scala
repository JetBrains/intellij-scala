package org.jetbrains.plugins.scala
package base
package libraryLoaders

import com.intellij.openapi.module.Module
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.vfs.{LocalFileSystem, VirtualFile}
import com.intellij.testFramework.PsiTestUtil

import java.io.File

case class SourcesLoader(rootPath: String) extends LibraryLoader {

  override def init(implicit module: Module, version: ScalaVersion): Unit = {
    FileUtil.createIfDoesntExist(new File(rootPath))
    PsiTestUtil.addSourceRoot(module, rootFile)
  }

  private def rootFile: VirtualFile =
    LocalFileSystem.getInstance.refreshAndFindFileByPath(rootPath)
}
