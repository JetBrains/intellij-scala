package org.jetbrains.plugins.scala.semantic

import org.jetbrains.plugins.scala.corpus.scala3.AkkaTest
import org.junit.Test

class AkkaSemanticTest extends SemanticTestBase(AkkaTest) {
  @Test def akkaActorActor(): Unit = doTest("akka.actor.Actor")

  @Test def akkaActorActorPath(): Unit = doTest("akka.actor.ActorPath")

  @Test def akkaActorTypedActor(): Unit = doTest("akka.actor.TypedActor")

  @Test def akkaStreamAttributes(): Unit = doTest("akka.stream.Attributes")

  @Test def akkaStreamFanInShape(): Unit = doTest("akka.stream.FanInShape")

  @Test def akkaStreamRestartSettings(): Unit = doTest("akka.stream.RestartSettings")

  @Test def akkaStreamSystemMaterializer(): Unit = doTest("akka.stream.SystemMaterializer")
}