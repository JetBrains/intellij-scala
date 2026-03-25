package org.jetbrains.plugins.scala.corpus
package scala2

import org.jetbrains.plugins.scala.DependencyManagerBase
import org.jetbrains.plugins.scala.DependencyManagerBase.RichStr

class ScalacticTest extends ProjectCorpusTestImpl(ScalacticTest)

object ScalacticTest extends Scala2ProjectCorpusTestDef {
  override val packages = Seq("org.scalactic")
  override val dependencies: Seq[DependencyManagerBase.DependencyDescription] = Seq(
    "org.scalactic" %% "scalactic" % "3.2.19",
  )
  override val includeScalaReflect = true
}
