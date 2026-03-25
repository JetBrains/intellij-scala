package org.jetbrains.plugins.scala.corpus
package scala2

import org.jetbrains.plugins.scala.DependencyManagerBase

class ScalaLibraryTest extends ProjectCorpusTestImpl(ScalaLibraryTest)

object ScalaLibraryTest extends Scala2ProjectCorpusTestDef {
  override val packages = Seq("scala")
  override val dependencies: Seq[DependencyManagerBase.DependencyDescription] = Seq.empty
}
