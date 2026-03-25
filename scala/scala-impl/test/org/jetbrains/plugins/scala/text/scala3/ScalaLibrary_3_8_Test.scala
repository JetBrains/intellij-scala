package org.jetbrains.plugins.scala.text
package scala3

import org.jetbrains.plugins.scala.DependencyManagerBase
import org.jetbrains.plugins.scala.{LatestScalaVersions, ScalaVersion}

class ScalaLibrary_3_8_Test extends ProjectCorpusTestImpl(ScalaLibrary_3_8_Test) {
  override protected def supportedIn(version: ScalaVersion) = version >= LatestScalaVersions.Scala_3_8

  override val includeScalaLibrarySources = true
}

object ScalaLibrary_3_8_Test extends Scala3ProjectCorpusTestDef {
  override val dependencies: Seq[DependencyManagerBase.DependencyDescription] = Seq.empty
}
