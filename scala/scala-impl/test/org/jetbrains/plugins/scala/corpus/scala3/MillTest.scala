package org.jetbrains.plugins.scala.corpus
package scala3

import org.jetbrains.plugins.scala.DependencyManagerBase
import org.jetbrains.plugins.scala.DependencyManagerBase.RichStr

class MillTest extends ProjectCorpusTestImpl(MillTest)

object MillTest extends Scala3ProjectCorpusTestDef {
  override val packages = Seq("mill")
  override val dependencies: Seq[DependencyManagerBase.DependencyDescription] = Seq(
    "com.lihaoyi" %% "mill-main" % "0.13.0-M1-43-b217bc",
  )
}
