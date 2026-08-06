package org.jetbrains.plugins.scala.corpus
package scala2

import org.jetbrains.plugins.scala.DependencyManagerBase
import org.jetbrains.plugins.scala.DependencyManagerBase.RichStr

class PlayTest extends ProjectCorpusTestImpl(PlayTest)

object PlayTest extends Scala2ProjectCorpusTestDef {
  override val packages = Seq("controllers", "models", "play", "views")
  override val dependencies: Seq[DependencyManagerBase.DependencyDescription] = Seq(
    "com.typesafe.play" %% "play" % "2.9.6",
  )
  override val includeScalaReflect = true
}
