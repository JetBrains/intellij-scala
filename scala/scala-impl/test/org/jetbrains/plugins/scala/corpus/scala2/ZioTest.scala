package org.jetbrains.plugins.scala.corpus
package scala2

import org.jetbrains.plugins.scala.DependencyManagerBase
import org.jetbrains.plugins.scala.DependencyManagerBase.RichStr

class ZioTest extends ProjectCorpusTestImpl(ZioTest)

object ZioTest extends Scala2ProjectCorpusTestDef {
  override val packages = Seq("zio")
  override val dependencies: Seq[DependencyManagerBase.DependencyDescription] = Seq(
    "dev.zio" %% "zio" % "2.1.23",
    "dev.zio" %% "zio-streams" % "2.1.23",
  )
  override val includeScalaReflect = true
}
