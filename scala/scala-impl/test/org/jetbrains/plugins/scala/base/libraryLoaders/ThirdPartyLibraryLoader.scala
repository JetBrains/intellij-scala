package org.jetbrains.plugins.scala.base.libraryLoaders

import java.io.File

import com.intellij.openapi.module.Module
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.vfs.newvfs.impl.VfsRootAccess
import com.intellij.testFramework.PsiTestUtil
import org.jetbrains.plugins.scala.base.libraryLoaders.IvyLibraryLoader._
import org.jetbrains.plugins.scala.debugger.{ScalaVersion, Scala_2_11}
import org.jetbrains.plugins.scala.project.ModuleExt

/**
  * @author adkozlov
  */
trait ThirdPartyLibraryLoader extends LibraryLoader {
  protected val name: String

  override def init(implicit module: Module, version: ScalaVersion): Unit = {
    val alreadyExistsInModule =
      module.libraries.map(_.getName)
        .contains(name)

    if (alreadyExistsInModule) return

    val path = this.path
    val file = new File(path).getCanonicalFile
    assert(file.exists(), s"library root for $name does not exist at $file")
    VfsRootAccess.allowRootAccess(path)
    PsiTestUtil.addLibrary(module, name, file.getParent, file.getName)
  }

  protected def path(implicit version: ScalaVersion): String
}