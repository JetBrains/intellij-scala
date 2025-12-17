package org.jetbrains.plugins.scala.text.scala3

import org.jetbrains.plugins.scala.DependencyManagerBase.RichStr
import org.jetbrains.plugins.scala.text.TextToTextTestBase

class ScalacheckTest extends TextToTextTestBase(
  dependencies = Seq(
    "org.scalacheck" %% "scalacheck" % "1.19.0",
  ),
  packages = Seq("org.scalacheck"),
  minClassCount = 39,
  withSources = true,
  sourceExceptions = Set(
    "org.scalacheck.Gen", // private type | N/A
    "org.scalacheck.Properties", // mutable.ListBuffer[String, Prop)] | Properties.this.props.type
    "org.scalacheck.commands.Commands", // private type | N/A
  )
)