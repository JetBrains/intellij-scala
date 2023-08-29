package org.jetbrains.plugins.scala.text.scala3

import org.jetbrains.plugins.scala.text.TextToTextTestBase

class ScalaLibraryTest extends TextToTextTestBase(
  Seq.empty,
  Seq("scala"), Set.empty, 91,
  Set(
    "scala.Tuple", // Top-level type alias
  )
)