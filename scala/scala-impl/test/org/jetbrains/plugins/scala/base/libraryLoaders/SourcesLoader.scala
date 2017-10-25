package org.jetbrains.plugins.scala.base.libraryLoaders

import java.io.File

import com.intellij.openapi.module.Module
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.vfs.{LocalFileSystem, VirtualFile}
import com.intellij.testFramework.PsiTestUtil
import org.jetbrains.plugins.scala.debugger.ScalaVersion

/**
  * @author adkozlov
  */
case class SourcesLoader(rootPath: String)
                        (implicit val module: Module) extends LibraryLoader {

  override def init(implicit version: ScalaVersion): Unit = {
    FileUtil.createIfDoesntExist(new File(rootPath))
    PsiTestUtil.addSourceRoot(module, rootFile)
  }

  override def dispose(): Unit = {
    PsiTestUtil.removeSourceRoot(module, rootFile)
  }

  private def rootFile: VirtualFile =
    LocalFileSystem.getInstance.refreshAndFindFileByPath(rootPath)
}
