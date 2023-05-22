package org.jetbrains.plugins.scala.text.scala2

import org.jetbrains.plugins.scala.DependencyManagerBase.RichStr
import org.jetbrains.plugins.scala.text.TextToTextTestBase

class CatsTest extends TextToTextTestBase(
  Seq(
    "org.typelevel" %% "cats-core" % "2.8.0",
    "org.typelevel" %% "cats-effect" % "3.3.14",
    "org.typelevel" %% "cats-free" % "2.8.0",
    "org.typelevel" %% "cats-laws" % "2.8.0",
  ),
  Seq("cats"), Set.empty, 1520,
  Set(
    "cats.arrow.FunctionKMacros", // Any
    "cats.arrow.FunctionKMacroMethods", // Any
    "cats.free.FreeFoldStep", // Incorrect types, Tuple2
    "cats.laws.NonEmptyParallelLaws", // Order in type refinement
    "cats.laws.ParallelLaws", // Order in type refinement
    "cats.laws.discipline.NonEmptyParallelTests", // Order in type refinement
    "cats.laws.discipline.ParallelTests", // Order in type refinement
  )
)