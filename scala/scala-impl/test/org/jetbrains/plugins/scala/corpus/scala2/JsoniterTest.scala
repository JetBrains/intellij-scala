package org.jetbrains.plugins.scala.corpus
package scala2

import org.jetbrains.plugins.scala.DependencyManagerBase
import org.jetbrains.plugins.scala.DependencyManagerBase.RichStr

class JsoniterTest extends ProjectCorpusTestImpl(JsoniterTest)

object JsoniterTest extends Scala2ProjectCorpusTestDef {
  override val packages = Seq("com.github.plokhotnyuk.jsoniter_scala")
  override val dependencies: Seq[DependencyManagerBase.DependencyDescription] = Seq(
    "com.github.plokhotnyuk.jsoniter-scala" %% "jsoniter-scala-macros" % "2.38.6",
  )
}
