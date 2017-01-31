package org.jetbrains.plugins.scala.base.libraryLoaders

import java.io.File

import com.intellij.openapi.module.Module
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.vfs.{LocalFileSystem, VirtualFile}
import com.intellij.testFramework.PsiTestUtil
import org.jetbrains.plugins.scala.util.TestUtils.ScalaSdkVersion

/**
  * @author adkozlov
  */
case class SourcesLoader(rootPath: String)
                        (implicit val module: Module) extends LibraryLoader {

  override def init(implicit version: ScalaSdkVersion): Unit = {
    FileUtil.createIfDoesntExist(new File(rootPath))
    PsiTestUtil.addSourceRoot(module, rootFile)

    LibraryLoader.storePointers()
  }

  override def clean(): Unit = {
    PsiTestUtil.removeSourceRoot(module, rootFile)
  }

  private def rootFile: VirtualFile =
    LocalFileSystem.getInstance.refreshAndFindFileByPath(rootPath)
}
