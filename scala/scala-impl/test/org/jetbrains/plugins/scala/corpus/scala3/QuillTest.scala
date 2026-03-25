package org.jetbrains.plugins.scala.corpus
package scala3

import org.jetbrains.plugins.scala.DependencyManagerBase
import org.jetbrains.plugins.scala.DependencyManagerBase.RichStr

class QuillTest extends ProjectCorpusTestImpl(QuillTest)

object QuillTest extends Scala3ProjectCorpusTestDef {
  override val packages = Seq("io.getquill")
  override val dependencies: Seq[DependencyManagerBase.DependencyDescription] = Seq(
    "io.getquill" %% "quill-sql" % "4.8.4",
    "io.getquill" %% "quill-jdbc-zio" % "4.8.4",
  )
}
