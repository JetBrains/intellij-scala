package org.jetbrains.plugins.scala.corpus
package scala3

import org.jetbrains.plugins.scala.DependencyManagerBase
import org.jetbrains.plugins.scala.DependencyManagerBase.RichStr

class CirceTest extends ProjectCorpusTestImpl(CirceTest)

object CirceTest extends Scala3ProjectCorpusTestDef {
  override val packages = Seq("io.circe")
  override val dependencies: Seq[DependencyManagerBase.DependencyDescription] = Seq(
    "io.circe" %% "circe-core" % "0.14.15",
    "io.circe" %% "circe-generic" % "0.14.15",
    "io.circe" %% "circe-parser" % "0.14.15",
  )
}
