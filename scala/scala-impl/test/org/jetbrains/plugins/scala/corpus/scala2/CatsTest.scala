package org.jetbrains.plugins.scala.corpus
package scala2

import org.jetbrains.plugins.scala.DependencyManagerBase
import org.jetbrains.plugins.scala.DependencyManagerBase.RichStr

class CatsTest extends ProjectCorpusTestImpl(CatsTest)

object CatsTest extends Scala2ProjectCorpusTestDef {
  override val packages = Seq("cats")
  override val dependencies: Seq[DependencyManagerBase.DependencyDescription] = Seq(
    "org.typelevel" %% "cats-core" % "2.13.0",
    "org.typelevel" %% "cats-effect" % "3.6.3",
    "org.typelevel" %% "cats-free" % "2.13.0",
    "org.typelevel" %% "cats-laws" % "2.13.0",
  )
}
