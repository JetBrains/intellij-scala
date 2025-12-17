package org.jetbrains.plugins.scala.text.scala3

import org.jetbrains.plugins.scala.DependencyManagerBase.RichStr
import org.jetbrains.plugins.scala.text.TextToTextTestBase

class JsoniterTest extends TextToTextTestBase(
  dependencies = Seq(
    "com.github.plokhotnyuk.jsoniter-scala" %% "jsoniter-scala-macros" % "2.38.6"
  ),
  packages = Seq("com.github.plokhotnyuk.jsoniter_scala"),
  minClassCount = 23,
  withSources = true
)