package org.jetbrains.plugins.scala.text
package scala3

import org.jetbrains.plugins.scala.DependencyManagerBase

class ScalaCompilerTest extends ProjectCorpusTestImpl(ScalaCompilerTest)

object ScalaCompilerTest extends Scala3ProjectCorpusTestDef {
  override val dependencies: Seq[DependencyManagerBase.DependencyDescription] = Seq.empty
  override val includeScalaCompiler = true
}
