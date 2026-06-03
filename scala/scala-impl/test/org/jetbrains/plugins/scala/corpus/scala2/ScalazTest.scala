package org.jetbrains.plugins.scala.corpus
package scala2

import org.jetbrains.plugins.scala.DependencyManagerBase
import org.jetbrains.plugins.scala.DependencyManagerBase.RichStr

class ScalazTest extends ProjectCorpusTestImpl(ScalazTest)

object ScalazTest extends Scala2ProjectCorpusTestDef {
  override val packages = Seq("scalaz")
  override val dependencies: Seq[DependencyManagerBase.DependencyDescription] = Seq(
    "org.scalaz" %% "scalaz-core" % "7.3.8",
    "org.scalaz" %% "scalaz-effect" % "7.3.8",
  )
}
