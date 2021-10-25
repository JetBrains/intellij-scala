package org.jetbrains.plugins.scala
package base
package libraryLoaders

import com.intellij.openapi.module.Module
import com.intellij.openapi.vfs.newvfs.impl.VfsRootAccess
import com.intellij.testFramework.PsiTestUtil
import org.jetbrains.plugins.scala.DependencyManagerBase.{DependencyDescription, ResolvedDependency}

import scala.annotation.nowarn
import scala.collection.mutable

abstract class IvyManagedLoaderBase extends LibraryLoader {

  protected def dependencyManager: DependencyManagerBase
  protected def dependencies(scalaVersion: ScalaVersion): Seq[DependencyDescription]
  protected def cache: mutable.Map[Seq[DependencyDescription], Seq[ResolvedDependency]]

  override def init(implicit module: Module, version: ScalaVersion): Unit = {
    val deps = dependencies(version)
    val resolved = cache.getOrElseUpdate(deps, dependencyManager.resolve(deps: _*))
    resolved.foreach { resolved =>
      VfsRootAccess.allowRootAccess(resolved.file.getCanonicalPath): @nowarn("cat=deprecation")
      PsiTestUtil.addLibrary(module, resolved.info.toString, resolved.file.getParent, resolved.file.getName)
    }
  }
}

final class IvyManagedLoader(
  override protected val dependencyManager: DependencyManagerBase,
  _dependencies: DependencyDescription*
) extends IvyManagedLoaderBase {

  def this(dependencies: DependencyDescription*) =
    this(TestDependencyManager, dependencies: _*)

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
    new IvyManagedLoader(dependencies: _*)

  def apply(dependencyManager: DependencyManagerBase, dependencies: DependencyDescription*): IvyManagedLoader =
    new IvyManagedLoader(dependencyManager, dependencies: _*)
}