package org.jetbrains.plugins.scala.text.scala3

import org.jetbrains.plugins.scala.DependencyManagerBase.RichStr
import org.jetbrains.plugins.scala.text.TextToTextTestBase

class ScalazTest extends TextToTextTestBase(
  Seq(
    "org.scalaz" %% "scalaz-core" % "7.3.7",
    "org.scalaz" %% "scalaz-effect" % "7.3.7",
  ),
  Seq("scalaz"), Set.empty, 1588,
  Set(
    "scalaz.Heap", // Excessive parentheses in function type
    "scalaz.\\&/", // id$
    "scalaz.\\/", // id$
  )
)