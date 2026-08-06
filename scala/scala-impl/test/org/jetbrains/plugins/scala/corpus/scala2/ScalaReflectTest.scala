package org.jetbrains.plugins.scala.corpus
package scala2

import org.jetbrains.plugins.scala.DependencyManagerBase

class ScalaReflectTest extends ProjectCorpusTestImpl(ScalaReflectTest)

object ScalaReflectTest extends Scala2ProjectCorpusTestDef {
  override val packages = Seq("scala.reflect")
  override val dependencies: Seq[DependencyManagerBase.DependencyDescription] = Seq.empty
  override val includeScalaReflect = true
}
