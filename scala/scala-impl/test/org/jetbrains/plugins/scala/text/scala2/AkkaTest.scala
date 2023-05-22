package org.jetbrains.plugins.scala.text.scala2

import org.jetbrains.plugins.scala.DependencyManagerBase.RichStr
import org.jetbrains.plugins.scala.text.TextToTextTestBase

class AkkaTest extends TextToTextTestBase(
  Seq(
    "com.typesafe.akka" %% "akka-actor" % "2.7.0",
    "com.typesafe.akka" %% "akka-actor-typed" % "2.7.0",
    "com.typesafe.akka" %% "akka-cluster" % "2.7.0",
    "com.typesafe.akka" %% "akka-http" % "10.5.0",
    "com.typesafe.akka" %% "akka-persistence" % "2.7.0",
    "com.typesafe.akka" %% "akka-stream" % "2.7.0",
  ),
  Seq("akka"), Set("akka.persistence.journal.leveldb", "akka.remote.artery.aeron", "akka.remote.transport.netty") /* External references */ , 2628,
  Set(
    "akka.dispatch.CachingConfig", // Existential type
    "akka.dispatch.ExecutorServiceDelegate", // Existential type
    "akka.http.impl.engine.rendering.HttpResponseRendererFactory", // No this. prefix for object
    "akka.http.impl.engine.server.HttpServerBluePrint", // Order in type refinement
    "akka.http.scaladsl.server.Directive", // By-name function type parameter
    "akka.http.scaladsl.server.directives.BasicDirectives", // Excessive parentheses in function type
    "akka.stream.Supervision", // Excessive parentheses in compound type
    "akka.stream.impl.QueueSource", // Order in type refinement
    "akka.stream.impl.VirtualProcessor", // No this. prefix for object
    "akka.stream.impl.io.ConnectionSourceStage", // Order in type refinement
    "akka.stream.impl.io.compression.DeflateDecompressor", // inflating.type is Any
    "akka.stream.scaladsl.MergeHub", // Cannot resolve Event
    "akka.stream.stage.GraphStageLogic", // Excessive parentheses in function type
  ),
  includeScalaReflect = true
)