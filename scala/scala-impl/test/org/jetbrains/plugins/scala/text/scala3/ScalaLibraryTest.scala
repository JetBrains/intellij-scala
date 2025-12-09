package org.jetbrains.plugins.scala.text.scala3

import org.jetbrains.plugins.scala.text.TextToTextTestBase

class ScalaLibraryTest extends TextToTextTestBase(
  Seq.empty,
  Seq("scala"), Set.empty, 91,
  Set(
    "scala.Tuple", // _ in match types, SCL-23189
  ),
  withSources = true,
  Set(
    "scala.CanThrow", // no annotations
    "scala.IArray", // cannot resolve IArray
    "scala.caps", // `*`
    "scala.annotation.MacroAnnotation", // x$1.reflect.Definition
    "scala.annotation.MainAnnotation", // duplicate annotation
    "scala.annotation.newMain", // no annotation
    "scala.quoted.Quotes", // Cannot resolve java.nio.file.Path
    "scala.quoted.runtime.QuoteMatching", // no <: _root_.scala.AnyKind
    "scala.quoted.runtime.QuoteUnpickler", // no <: _root_.scala.AnyKind
    "scala.runtime.coverage.Invoker", // no annotation
    "scala.util.TupledFunction", // duplicate annotation
  )
) {
  override protected val includeScalaLibrarySources: Boolean = true
}
