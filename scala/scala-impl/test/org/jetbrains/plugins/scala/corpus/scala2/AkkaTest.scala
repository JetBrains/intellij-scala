package org.jetbrains.plugins.scala.corpus
package scala2

import org.jetbrains.plugins.scala.DependencyManagerBase
import org.jetbrains.plugins.scala.DependencyManagerBase.RichStr

class AkkaTest extends ProjectCorpusTestImpl(AkkaTest)

object AkkaTest extends Scala2ProjectCorpusTestDef {
  override val packages = Seq("akka")
  override val dependencies: Seq[DependencyManagerBase.DependencyDescription] = Seq(
    "com.typesafe.akka" %% "akka-actor" % "2.8.8",
    "com.typesafe.akka" %% "akka-actor-typed" % "2.8.8",
    "com.typesafe.akka" %% "akka-cluster" % "2.8.8",
    "com.typesafe.akka" %% "akka-http" % "10.5.3",
    "com.typesafe.akka" %% "akka-persistence" % "2.8.8",
    "com.typesafe.akka" %% "akka-stream" % "2.8.8",
  )
  override val includeScalaReflect = true
}