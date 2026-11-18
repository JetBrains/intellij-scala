package org.jetbrains.plugins.scala.corpus
package scala3

import org.jetbrains.plugins.scala.{DependencyManagerBase, LatestScalaVersions, ScalaVersion}

class ScalaLibrary_3_10_Test extends ProjectCorpusTestImpl(ScalaLibrary_3_10_Test) {
  override protected def supportedIn(version: ScalaVersion): Boolean = version == LatestScalaVersions.Scala_3_10

  override val includeScalaLibrarySources = true
}

object ScalaLibrary_3_10_Test extends Scala3ProjectCorpusTestDef {
  override val packages = Seq("scala")
  override val dependencies: Seq[DependencyManagerBase.DependencyDescription] = Seq.empty
}
