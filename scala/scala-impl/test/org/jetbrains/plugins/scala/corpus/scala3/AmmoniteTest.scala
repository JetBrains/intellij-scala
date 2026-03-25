package org.jetbrains.plugins.scala.corpus
package scala3

import org.jetbrains.plugins.scala.DependencyManagerBase
import org.jetbrains.plugins.scala.DependencyManagerBase.RichStr

class AmmoniteTest extends ProjectCorpusTestImpl(AmmoniteTest)

object AmmoniteTest extends Scala3ProjectCorpusTestDef {
  override val packages = Seq("ammonite")
  override val dependencies: Seq[DependencyManagerBase.DependencyDescription] = Seq(
    "com.lihaoyi" % "ammonite_3.3.7" % "3.0.6",
  )
}
