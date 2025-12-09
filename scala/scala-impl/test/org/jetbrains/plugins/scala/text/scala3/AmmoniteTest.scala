package org.jetbrains.plugins.scala.text.scala3

import org.jetbrains.plugins.scala.DependencyManagerBase.RichStr
import org.jetbrains.plugins.scala.text.TextToTextTestBase

class AmmoniteTest extends TextToTextTestBase(
  dependencies = Seq(
    "com.lihaoyi" % "ammonite_3.3.4" % "3.0.2",
  ),
  packages = Seq("ammonite"),
  minClassCount = 161,
  classExceptions = Set(
    "ammonite.repl.Repl", // Unknown vs Any
  )
)
