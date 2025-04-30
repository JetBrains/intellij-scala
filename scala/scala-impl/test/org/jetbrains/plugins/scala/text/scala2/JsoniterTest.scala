package org.jetbrains.plugins.scala.text.scala2

import org.jetbrains.plugins.scala.DependencyManagerBase.RichStr
import org.jetbrains.plugins.scala.text.TextToTextTestBase

class JsoniterTest extends TextToTextTestBase(
  Seq(
    "com.github.plokhotnyuk.jsoniter-scala" %% "jsoniter-scala-macros" % "2.35.1"
  ),
  Seq("com.github.plokhotnyuk.jsoniter_scala"), Set.empty, 15,
  Set.empty
)