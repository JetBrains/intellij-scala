package org.jetbrains.plugins.scala
package base
package libraryLoaders

import java.io.File

import com.intellij.openapi.module.Module
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.vfs.{LocalFileSystem, VirtualFile}
import com.intellij.testFramework.PsiTestUtil

/**
  * @author adkozlov
  */
case class SourcesLoader(rootPath: String) extends LibraryLoader {

  override def init(implicit module: Module, version: ScalaVersion): Unit = {
    FileUtil.createIfDoesntExist(new File(rootPath))
    PsiTestUtil.addSourceRoot(module, rootFile)
  }

  override def clean(implicit module: Module): Unit = {
    PsiTestUtil.removeSourceRoot(module, rootFile)
  }

  private def rootFile: VirtualFile =
    LocalFileSystem.getInstance.refreshAndFindFileByPath(rootPath)
}
