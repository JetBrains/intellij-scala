package org.jetbrains.plugins.scala
package base
package libraryLoaders

import com.intellij.openapi.Disposable
import com.intellij.openapi.module.Module
import com.intellij.openapi.vfs.newvfs.impl.VfsRootAccess
import com.intellij.testFramework.PsiTestUtil
import org.jetbrains.plugins.scala.DependencyManagerBase.{DependencyDescription, ResolvedDependency, Types}
import org.jetbrains.plugins.scala.util.dependencymanager.TestDependencyManagers

import scala.collection.mutable

abstract class IvyManagedLoaderBase extends LibraryLoader {

  protected def dependencyManager: DependencyManagerBase
  protected def dependencies(scalaVersion: ScalaVersion): Seq[DependencyDescription]
  protected def cache: mutable.Map[Seq[DependencyDescription], Seq[ResolvedDependency]]

  override def init(implicit module: Module, version: ScalaVersion): Unit = init(None)

  /**
   * Adds resolved dependencies as libraries to the specified module
   * @param parentDisposable if specified, used to remove libraries from the module on parentDisposable's disposing
   */
  def init(parentDisposable: Option[Disposable])(implicit module: Module, version: ScalaVersion): Unit = {
    val deps = dependencies(version)
    val resolved = cache.getOrElseUpdate(deps, dependencyManager.resolve(deps: _*))
    resolved.foreach { resolved =>
      val resolvedFile = resolved.file.toFile
      VfsRootAccess.allowRootAccess(module, resolvedFile.getCanonicalPath)

      parentDisposable match {
        case Some(disposable) =>
          PsiTestUtil.addLibrary(disposable, module, resolved.info.toString, resolvedFile.getParent, resolvedFile.getName)
        case _ =>
          PsiTestUtil.addLibrary(module, resolved.info.toString, resolvedFile.getParent, resolvedFile.getName)
      }
    }
  }
}

final class IvyManagedLoader private(
  override protected val dependencyManager: DependencyManagerBase,
  private val _dependencies: DependencyDescription*
) extends IvyManagedLoaderBase {

  _dependencies.foreach { it =>
    require(!(it.kind == Types.SRC && it.isTransitive), "Transitive source dependencies are not supported in Ivy") // https://issues.apache.org/jira/browse/IVY-1003
  }

  override protected def cache: mutable.Map[Seq[DependencyDescription], Seq[ResolvedDependency]] =
    IvyManagedLoader.cache

  override protected def dependencies(unused: ScalaVersion): Seq[DependencyDescription] =
    _dependencies

  // TODO Support transitive sources directly, via LibraryLoader.init(..., sources: DependencyDescription => Boolean)
  def resolve(version: ScalaVersion): Seq[ResolvedDependency] = {
    val deps = dependencies(version)
    cache.getOrElseUpdate(deps, dependencyManager.resolve(deps: _*))
  }

  /**
   * NOTE: equals & hashCode are needed for test execution time optimization,
   * in order [[org.jetbrains.plugins.scala.base.SharedTestProjectToken.ByTestClassAndScalaSdkAndProjectLibraries]]
   * correctly identifies uniqueness of libraries
   */
  override def equals(other: Any): Boolean = other match {
    case that: IvyManagedLoader =>
      dependencyManager == that.dependencyManager &&
        _dependencies == that._dependencies
    case _ => false
  }

  override def hashCode(): Int = {
    val state = Seq(dependencyManager, _dependencies)
    state.map(_.hashCode()).foldLeft(0)((a, b) => 31 * a + b)
  }
}

object IvyManagedLoader {

  private val cache: mutable.Map[
    Seq[DependencyDescription],
    Seq[ResolvedDependency]
  ] = mutable.Map()

  def apply(dependencies: DependencyDescription*): IvyManagedLoader =
    new IvyManagedLoader(TestDependencyManagers.IgnoringAllScalaArtifacts, dependencies: _*)

  def apply(dependencyManager: DependencyManagerBase, dependencies: DependencyDescription*): IvyManagedLoader =
    new IvyManagedLoader(dependencyManager, dependencies: _*)
}
