package org.jetbrains.plugins.scala.text
package scala3

import org.jetbrains.plugins.scala.DependencyManagerBase
import org.jetbrains.plugins.scala.DependencyManagerBase.RichStr

class ScalacticTest extends ProjectCorpusTestImpl(ScalacticTest)

object ScalacticTest extends Scala3ProjectCorpusTestDef {
  override val dependencies: Seq[DependencyManagerBase.DependencyDescription] = Seq(
    "org.scalactic" %% "scalactic" % "3.2.19",
  )
}
