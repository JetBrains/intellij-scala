package org.jetbrains.plugins.scala.text
package scala2

import org.jetbrains.plugins.scala.DependencyManagerBase
import org.jetbrains.plugins.scala.DependencyManagerBase.RichStr

class Fs2Test extends ProjectCorpusTestImpl(Fs2Test)

object Fs2Test extends Scala2ProjectCorpusTestDef {
  override val dependencies: Seq[DependencyManagerBase.DependencyDescription] = Seq(
    "co.fs2" %% "fs2-core" % "3.12.2",
  )
}
