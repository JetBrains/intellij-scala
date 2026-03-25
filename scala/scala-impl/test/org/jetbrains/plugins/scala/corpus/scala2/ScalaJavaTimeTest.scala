package org.jetbrains.plugins.scala.corpus
package scala2

import org.jetbrains.plugins.scala.DependencyManagerBase
import org.jetbrains.plugins.scala.DependencyManagerBase.RichStr

class ScalaJavaTimeTest extends ProjectCorpusTestImpl(ScalaJavaTimeTest)

object ScalaJavaTimeTest extends Scala2ProjectCorpusTestDef {
  override val packages = Seq("java.time", "java.util")
  override val dependencies: Seq[DependencyManagerBase.DependencyDescription] = Seq(
    "io.github.cquiroz" %% "scala-java-time" % "2.6.0",
    "io.github.cquiroz" %% "scala-java-time_sjs1" % "2.6.0",
    "io.github.cquiroz" %% "scala-java-time_native0.5" % "2.6.0",
  )
}
