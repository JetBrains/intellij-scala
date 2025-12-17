package org.jetbrains.plugins.scala.text.scala2

import org.jetbrains.plugins.scala.text.TextToTextTestBase

class ScalaLibraryTest extends TextToTextTestBase(
  dependencies = Seq.empty,
  packages = Seq("scala"),
  minClassCount = 787,
  classExceptions = Set(
    "scala.concurrent.impl.Promise", // Function1
  )
)