package org.jetbrains.plugins.scala.text.scala3

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
  Seq("akka"), Set("akka.parboiled2", "akka.persistence.journal.leveldb", "akka.remote.artery.aeron", "akka.remote.transport.netty") /* External references */ , 2582,
  Set(
    "akka.actor.typed.internal.receptionist.Platform", // Match type case without qualifier
    "akka.http.impl.model.parser.CommonRules", // Any
    "akka.http.impl.model.parser.SimpleHeaders", // .Out
    "akka.http.impl.util.JavaMapping", // Cannot resolve S, J
    "akka.http.scaladsl.server.Directive", // By-name function type parameter
    "akka.http.scaladsl.server.directives.BasicDirectives",
    "akka.http.scaladsl.server.util.BinaryPolyFunc", // Unknown
    "akka.stream.scaladsl.MergeHub", // Cannot resolve Event
    "akka.stream.stage.GraphStageLogic", // Excessive parentheses in function type
  )
)