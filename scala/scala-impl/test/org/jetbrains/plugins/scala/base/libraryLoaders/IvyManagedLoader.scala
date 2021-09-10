package org.jetbrains.plugins.scala
package base
package libraryLoaders

import com.intellij.openapi.module.Module
import com.intellij.openapi.vfs.newvfs.impl.VfsRootAccess
import com.intellij.testFramework.PsiTestUtil
import org.jetbrains.plugins.scala.DependencyManagerBase.{DependencyDescription, ResolvedDependency}

import scala.annotation.nowarn
import scala.collection.mutable

case class IvyManagedLoader(dependencies: DependencyDescription*) extends LibraryLoader {
  protected lazy val dependencyManager: DependencyManagerBase = TestDependencyManager

  override def init(implicit module: Module, version: ScalaVersion): Unit = {
    val resolved = IvyManagedLoader.cache.getOrElseUpdate(
      dependencies,
      dependencyManager.resolve(dependencies: _*)
    )
    resolved.foreach { resolved =>
      VfsRootAccess.allowRootAccess(module, resolved.file.getCanonicalPath): @nowarn("cat=deprecation")
      PsiTestUtil.addLibrary(module, resolved.info.toString, resolved.file.getParent, resolved.file.getName)
    }
  }
}

object IvyManagedLoader {

  private val cache: mutable.Map[
    Seq[DependencyDescription],
    Seq[ResolvedDependency]
  ] = mutable.Map()
}