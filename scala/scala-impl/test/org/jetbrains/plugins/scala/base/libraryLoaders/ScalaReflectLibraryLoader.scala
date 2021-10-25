package org.jetbrains.plugins.scala.base.libraryLoaders

import org.jetbrains.plugins.scala.DependencyManagerBase.{DependencyDescription, ResolvedDependency, RichStr}
import org.jetbrains.plugins.scala.{DependencyManager, DependencyManagerBase, ScalaVersion}

import scala.collection.mutable

object ScalaReflectLibraryLoader extends IvyManagedLoaderBase {
  override protected def dependencyManager: DependencyManagerBase = DependencyManager

  override protected def dependencies(scalaVersion: ScalaVersion): Seq[DependencyManagerBase.DependencyDescription] =
    Seq(
      "org.scala-lang" % "scala-reflect" % scalaVersion.minor
    )

  override protected val cache: mutable.Map[
    Seq[DependencyDescription],
    Seq[ResolvedDependency]
  ] = mutable.Map()
}