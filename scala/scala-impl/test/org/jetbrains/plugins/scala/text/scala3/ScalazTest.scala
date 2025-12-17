package org.jetbrains.plugins.scala.text.scala3

import org.jetbrains.plugins.scala.DependencyManagerBase.RichStr
import org.jetbrains.plugins.scala.text.TextToTextTestBase

class ScalazTest extends TextToTextTestBase(
  dependencies = Seq(
    "org.scalaz" %% "scalaz-core" % "7.3.8",
    "org.scalaz" %% "scalaz-effect" % "7.3.8",
  ),
  packages = Seq("scalaz"),
  minClassCount = 1588,
  classExceptions = Set(
    "scalaz.\\&/", // id$
    "scalaz.\\/", // id$
  )
)