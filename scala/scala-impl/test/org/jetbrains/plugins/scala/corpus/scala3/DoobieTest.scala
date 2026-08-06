package org.jetbrains.plugins.scala.corpus
package scala3

import org.jetbrains.plugins.scala.DependencyManagerBase
import org.jetbrains.plugins.scala.DependencyManagerBase.RichStr

class DoobieTest extends ProjectCorpusTestImpl(DoobieTest)

object DoobieTest extends Scala3ProjectCorpusTestDef {
  override val packages = Seq("doobie")
  override val dependencies: Seq[DependencyManagerBase.DependencyDescription] = Seq(
    "org.tpolecat" %% "doobie-core" % "1.0.0-RC11",
  )
}
