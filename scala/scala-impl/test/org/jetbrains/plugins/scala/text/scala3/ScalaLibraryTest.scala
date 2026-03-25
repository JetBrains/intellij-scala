package org.jetbrains.plugins.scala.text
package scala3

import org.jetbrains.plugins.scala.DependencyManagerBase

class ScalaLibraryTest extends ProjectCorpusTestImpl(ScalaLibraryTest) {
  override val includeScalaLibrarySources = true
}

object ScalaLibraryTest extends Scala3ProjectCorpusTestDef {
  override val dependencies: Seq[DependencyManagerBase.DependencyDescription] = Seq.empty
}
