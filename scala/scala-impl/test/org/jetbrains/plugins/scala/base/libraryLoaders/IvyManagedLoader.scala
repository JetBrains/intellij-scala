package org.jetbrains.plugins.scala
package base
package libraryLoaders

import com.intellij.openapi.module.Module
import com.intellij.openapi.vfs.newvfs.impl.VfsRootAccess
import com.intellij.testFramework.PsiTestUtil
import org.jetbrains.plugins.scala.DependencyManagerBase.{DependencyDescription, ResolvedDependency}

import scala.annotation.nowarn
import scala.collection.mutable

final class IvyManagedLoader(
  dependencyManager: DependencyManagerBase,
  dependencies: DependencyDescription*
) extends LibraryLoader {

  def this(dependencies: DependencyDescription*) =
    this(TestDependencyManager, dependencies: _*)

  override def init(implicit module: Module, version: ScalaVersion): Unit = {
    val resolved = IvyManagedLoader.cache.getOrElseUpdate(
      dependencies,
      dependencyManager.resolve(dependencies: _*)
    )
    resolved.foreach { resolved =>
      VfsRootAccess.allowRootAccess(resolved.file.getCanonicalPath): @nowarn("cat=deprecation")
      PsiTestUtil.addLibrary(module, resolved.info.toString, resolved.file.getParent, resolved.file.getName)
    }
  }
}

object IvyManagedLoader {

  def apply(dependencies: DependencyDescription*): IvyManagedLoader =
    new IvyManagedLoader(dependencies: _*)

  def apply(dependencyManager: DependencyManagerBase, dependencies: DependencyDescription*): IvyManagedLoader =
    new IvyManagedLoader(dependencyManager, dependencies: _*)

  private val cache: mutable.Map[
    Seq[DependencyDescription],
    Seq[ResolvedDependency]
  ] = mutable.Map()
}