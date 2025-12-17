package org.jetbrains.plugins.scala.text.scala3

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
  packageExceptions = Set("akka.parboiled2", "akka.persistence.journal.leveldb", "akka.remote.artery.aeron", "akka.remote.transport.netty") /* External references */ ,
  minClassCount = 2521,
  classExceptions = Set(
    "akka.actor.typed.internal.receptionist.Platform", // Match type case without qualifier
    "akka.http.impl.model.parser.CommonRules", // HList type reduction
    "akka.http.impl.model.parser.SimpleHeaders", // HList type reduction
    "akka.http.impl.util.JavaMapping", // Cannot resolve S, J
    "akka.http.scaladsl.server.Directive", // By-name function type parameter, SCL-21149
    "akka.http.scaladsl.server.util.BinaryPolyFunc", // Unknown
    "akka.stream.scaladsl.MergeHub", // private method references private class (skip private[OuterClass] methods?)
  )
)