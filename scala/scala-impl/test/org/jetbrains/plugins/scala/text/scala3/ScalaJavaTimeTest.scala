package org.jetbrains.plugins.scala.text.scala3

import org.jetbrains.plugins.scala.DependencyManagerBase.RichStr
import org.jetbrains.plugins.scala.text.TextToTextTestBase

class ScalaJavaTimeTest extends TextToTextTestBase(
  Seq(
    "io.github.cquiroz" % "scala-java-time_3" % "2.5.0",
    "io.github.cquiroz" % "scala-java-time_sjs1_3" % "2.5.0",
    "io.github.cquiroz" % "scala-java-time_native0.4_3" % "2.5.0",
  ),
  Seq("java.time", "java.util"), Set.empty, 162,
  Set(
    "java.time.temporal.TemporalAdjusters", // Private object reference
    "java.time.zone.ZoneRulesBuilder", // Private object reference
  )
)