package org.jetbrains.plugins.scala
package base
package libraryLoaders

import com.intellij.openapi.module.Module
import com.intellij.openapi.vfs.newvfs.impl.VfsRootAccess
import com.intellij.testFramework.PsiTestUtil
import org.jetbrains.plugins.scala.project.ModuleExt

import java.io.File
import scala.annotation.nowarn

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
    VfsRootAccess.allowRootAccess(module, path): @nowarn("cat=deprecation")
    PsiTestUtil.addLibrary(module, name, file.getParent, file.getName)
  }

  protected def path(implicit version: ScalaVersion): String
}