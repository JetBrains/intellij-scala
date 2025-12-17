package org.jetbrains.plugins.scala.text.scala2

import org.jetbrains.plugins.scala.DependencyManagerBase.RichStr
import org.jetbrains.plugins.scala.text.TextToTextTestBase

class AkkaTest extends TextToTextTestBase(
  dependencies = Seq(
    "com.typesafe.akka" %% "akka-actor" % "2.8.8",
    "com.typesafe.akka" %% "akka-actor-typed" % "2.8.8",
    "com.typesafe.akka" %% "akka-cluster" % "2.8.8",
    "com.typesafe.akka" %% "akka-http" % "10.5.3",
    "com.typesafe.akka" %% "akka-persistence" % "2.8.8",
    "com.typesafe.akka" %% "akka-stream" % "2.8.8",
  ),
  packages = Seq("akka"),
  packageExceptions = Set("akka.persistence.journal.leveldb", "akka.remote.artery.aeron", "akka.remote.transport.netty") /* External references */ ,
  minClassCount = 2567,
  classExceptions = Set(
    "akka.dispatch.CachingConfig", // Existential type
    "akka.dispatch.ExecutorServiceDelegate", // Existential type
    "akka.http.impl.engine.rendering.HttpResponseRendererFactory", // No this. prefix for object
    "akka.http.impl.engine.server.HttpServerBluePrint", // Order in type refinement
    "akka.http.scaladsl.server.Directive", // By-name function type parameter
    "akka.stream.Supervision", // Excessive parentheses in compound type
    "akka.stream.impl.QueueSource", // Order in type refinement
    "akka.stream.impl.VirtualProcessor", // No this. prefix for object
    "akka.stream.impl.io.ConnectionSourceStage", // Order in type refinement
    "akka.stream.impl.io.compression.DeflateDecompressor", // inflating.type is Any
    "akka.stream.scaladsl.MergeHub", // Cannot resolve Event
  ),
  includeScalaReflect = true
)