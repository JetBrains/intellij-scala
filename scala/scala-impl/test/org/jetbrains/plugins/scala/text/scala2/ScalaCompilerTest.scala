package org.jetbrains.plugins.scala.text
package scala2

import org.jetbrains.plugins.scala.DependencyManagerBase
import org.jetbrains.plugins.scala.DependencyManagerBase.RichStr

class ScalaCompilerTest extends ProjectCorpusTestImpl(ScalaCompilerTest)

object ScalaCompilerTest extends Scala2ProjectCorpusTestDef {
  override val dependencies: Seq[DependencyManagerBase.DependencyDescription] = Seq(
    "org.jline" % "jline" % "3.21.0",
  )
  override val includeScalaReflect = true
  override val includeScalaCompiler = true
}
