package org.jetbrains.plugins.scala.text.scala2

import org.jetbrains.plugins.scala.DependencyManagerBase.RichStr
import org.jetbrains.plugins.scala.text.TextToTextTestBase

class ScalaJavaTimeTest extends TextToTextTestBase(
  Seq(
    "io.github.cquiroz" % "scala-java-time_2.13" % "2.5.0",
    "io.github.cquiroz" % "scala-java-time_sjs1_2.13" % "2.5.0",
    "io.github.cquiroz" % "scala-java-time_native0.4_2.13" % "2.5.0",
  ),
  Seq("java.time", "java.util"), Set.empty, 186,
  Set(
    "java.time.temporal.TemporalAdjusters", // Private object reference
    "java.time.zone.ZoneRulesBuilder", // Private object reference
  )
)