package org.jetbrains.plugins.scala.text
package scala2

import org.jetbrains.plugins.scala.DependencyManagerBase
import org.jetbrains.plugins.scala.DependencyManagerBase.RichStr

class MillTest extends ProjectCorpusTestImpl(MillTest)

object MillTest extends Scala2ProjectCorpusTestDef {
  override val dependencies: Seq[DependencyManagerBase.DependencyDescription] = Seq(
    "com.lihaoyi" %% "mill-main" % "0.12.15",
  )
  override val includeScalaReflect = true
}
