package org.jetbrains.plugins.scala.text.scala2

import org.jetbrains.plugins.scala.text.TextToTextTestBase

class ScalaLibraryTest extends TextToTextTestBase(
  Seq.empty,
  Seq("scala"), Set.empty, 781,
  Set(
    "scala.concurrent.impl.Promise", // Function1
  )
)