package org.jetbrains.plugins.scala.corpus
package scala2

import org.jetbrains.plugins.scala.DependencyManagerBase
import org.jetbrains.plugins.scala.DependencyManagerBase.RichStr

class AmmoniteTest extends ProjectCorpusTestImpl(AmmoniteTest)

object AmmoniteTest extends Scala2ProjectCorpusTestDef {
  override val packages = Seq("ammonite")
  override val dependencies: Seq[DependencyManagerBase.DependencyDescription] = Seq(
    "com.lihaoyi" % "ammonite_2.13.18" % "3.0.6",
  )
  override val includeScalaCompiler = true
  override val includeScalaReflect = true
}
