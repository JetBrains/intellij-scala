package org.jetbrains.plugins.scala.text.scala3

import org.jetbrains.plugins.scala.DependencyManagerBase.RichStr
import org.jetbrains.plugins.scala.text.TextToTextTestBase

class JsoniterTest extends TextToTextTestBase(
  Seq(
    "com.github.plokhotnyuk.jsoniter-scala" %% "jsoniter-scala-macros" % "2.35.1"
  ),
  Seq("com.github.plokhotnyuk.jsoniter_scala"), Set.empty, 22,
  Set.empty,
  withSources = true,
  Set(
    "com.github.plokhotnyuk.jsoniter_scala.macros.NameMapper" // Suitable method not found
  )
)