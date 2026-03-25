package org.jetbrains.plugins.scala.corpus
package scala3

import org.jetbrains.plugins.scala.DependencyManagerBase
import org.jetbrains.plugins.scala.DependencyManagerBase.RichStr

class ScalatestTest extends ProjectCorpusTestImpl(ScalatestTest)

object ScalatestTest extends Scala3ProjectCorpusTestDef {
  override val packages = Seq("org.scalatest")
  override val dependencies: Seq[DependencyManagerBase.DependencyDescription] = Seq(
    "org.scalatest" %% "scalatest" % "3.2.19",
  )
}
