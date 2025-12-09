package org.jetbrains.plugins.scala.text.scala2

import org.jetbrains.plugins.scala.DependencyManagerBase.RichStr
import org.jetbrains.plugins.scala.text.TextToTextTestBase

class ScalaJavaTimeTest extends TextToTextTestBase(
  dependencies = Seq(
    "io.github.cquiroz" % "scala-java-time_2.13" % "2.5.0",
    "io.github.cquiroz" % "scala-java-time_sjs1_2.13" % "2.5.0",
    "io.github.cquiroz" % "scala-java-time_native0.4_2.13" % "2.5.0",
  ),
  packages = Seq("java.time", "java.util"),
  minClassCount = 186,
  classExceptions = Set(
    "java.time.temporal.TemporalAdjusters", // Private object reference
    "java.time.zone.ZoneRulesBuilder", // Private object reference
  )
)