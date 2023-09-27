package org.jetbrains.plugins.scala
package base
package libraryLoaders

import com.intellij.openapi.module.Module
import com.intellij.openapi.vfs.newvfs.impl.VfsRootAccess
import com.intellij.testFramework.PsiTestUtil
import org.jetbrains.plugins.scala.DependencyManagerBase.{DependencyDescription, ResolvedDependency}
import org.jetbrains.plugins.scala.util.dependencymanager.TestDependencyManager

import scala.collection.mutable

abstract class IvyManagedLoaderBase extends LibraryLoader {

  protected def dependencyManager: DependencyManagerBase
  protected def dependencies(scalaVersion: ScalaVersion): Seq[DependencyDescription]
  protected def cache: mutable.Map[Seq[DependencyDescription], Seq[ResolvedDependency]]

  override def init(implicit module: Module, version: ScalaVersion): Unit = {
    val deps = dependencies(version)
    val resolved = cache.getOrElseUpdate(deps, dependencyManager.resolve(deps: _*))
    resolved.foreach { resolved =>
      VfsRootAccess.allowRootAccess(module, resolved.file.getCanonicalPath)
      PsiTestUtil.addLibrary(module, resolved.info.toString, resolved.file.getParent, resolved.file.getName)
    }
  }
}

final class IvyManagedLoader private(
  override protected val dependencyManager: DependencyManagerBase,
  _dependencies: DependencyDescription*
) extends IvyManagedLoaderBase {

  override protected def cache: mutable.Map[Seq[DependencyDescription], Seq[ResolvedDependency]] =
    IvyManagedLoader.cache

  override protected def dependencies(unused: ScalaVersion): Seq[DependencyDescription] =
    _dependencies
}

object IvyManagedLoader {

  private val cache: mutable.Map[
    Seq[DependencyDescription],
    Seq[ResolvedDependency]
  ] = mutable.Map()

  def apply(dependencies: DependencyDescription*): IvyManagedLoader =
    new IvyManagedLoader(TestDependencyManager, dependencies: _*)

  def apply(dependencyManager: DependencyManagerBase, dependencies: DependencyDescription*): IvyManagedLoader =
    new IvyManagedLoader(dependencyManager, dependencies: _*)
}