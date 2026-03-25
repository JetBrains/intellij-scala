package org.jetbrains.plugins.scala.corpus
package scala2

import org.jetbrains.plugins.scala.DependencyManagerBase
import org.jetbrains.plugins.scala.DependencyManagerBase.RichStr

class ScalacheckTest extends ProjectCorpusTestImpl(ScalacheckTest)

object ScalacheckTest extends Scala2ProjectCorpusTestDef {
  override val packages = Seq("org.scalacheck")
  override val dependencies: Seq[DependencyManagerBase.DependencyDescription] = Seq(
    "org.scalacheck" %% "scalacheck" % "1.19.0",
  )
}
