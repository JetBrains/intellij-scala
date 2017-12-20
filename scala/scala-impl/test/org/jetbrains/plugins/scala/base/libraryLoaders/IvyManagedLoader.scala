package org.jetbrains.plugins.scala.base.libraryLoaders

import com.intellij.openapi.module.Module
import com.intellij.openapi.vfs.newvfs.impl.VfsRootAccess
import com.intellij.testFramework.PsiTestUtil
import org.jetbrains.plugins.scala.debugger.ScalaVersion
import org.jetbrains.plugins.scala.DependencyManager
import org.jetbrains.plugins.scala.DependencyManager._

class IvyManagedLoader(dependencies: Dependency*) extends LibraryLoader {
  override def init(implicit module: Module, version: ScalaVersion): Unit = {
    DependencyManager().resolve(dependencies: _*).foreach { resolved =>
      VfsRootAccess.allowRootAccess(resolved.file.getCanonicalPath)
      PsiTestUtil.addLibrary(module, resolved.info.toString, resolved.file.getParent, resolved.file.getName)
    }
  }

}

object IvyManagedLoader {
  def apply(dependencies: Dependency*): IvyManagedLoader = new IvyManagedLoader(dependencies: _*)
}
