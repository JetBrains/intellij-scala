package org.jetbrains.plugins.scala.text.scala2

import org.jetbrains.plugins.scala.DependencyManagerBase.RichStr
import org.jetbrains.plugins.scala.text.TextToTextTestBase

class ScalaJavaTimeTest extends TextToTextTestBase(
  dependencies = Seq(
    "io.github.cquiroz" %% "scala-java-time" % "2.6.0",
    "io.github.cquiroz" %% "scala-java-time_sjs1" % "2.6.0",
    "io.github.cquiroz" %% "scala-java-time_native0.5" % "2.6.0",
  ),
  packages = Seq("java.time", "java.util"),
  minClassCount = 186,
  classExceptions = Set(
    "java.time.temporal.TemporalAdjusters", // Private object reference
    "java.time.zone.ZoneRulesBuilder", // Private object reference
  )
)