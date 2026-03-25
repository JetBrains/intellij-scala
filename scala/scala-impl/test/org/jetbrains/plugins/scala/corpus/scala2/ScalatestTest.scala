package org.jetbrains.plugins.scala.corpus
package scala2

import org.jetbrains.plugins.scala.DependencyManagerBase
import org.jetbrains.plugins.scala.DependencyManagerBase.RichStr

class ScalatestTest extends ProjectCorpusTestImpl(ScalatestTest)

object ScalatestTest extends Scala2ProjectCorpusTestDef {
  override val packages = Seq("org.scalatest")
  override val dependencies: Seq[DependencyManagerBase.DependencyDescription] = Seq(
    "org.scalatest" %% "scalatest" % "3.2.19",
  )
  override val includeScalaReflect = true
}
