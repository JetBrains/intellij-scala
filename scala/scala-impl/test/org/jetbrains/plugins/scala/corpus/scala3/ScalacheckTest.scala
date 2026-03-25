package org.jetbrains.plugins.scala.corpus
package scala3

import org.jetbrains.plugins.scala.DependencyManagerBase
import org.jetbrains.plugins.scala.DependencyManagerBase.RichStr

class ScalacheckTest extends ProjectCorpusTestImpl(ScalacheckTest)

object ScalacheckTest extends Scala3ProjectCorpusTestDef {
  override val dependencies: Seq[DependencyManagerBase.DependencyDescription] = Seq(
    "org.scalacheck" %% "scalacheck" % "1.19.0",
  )
}
