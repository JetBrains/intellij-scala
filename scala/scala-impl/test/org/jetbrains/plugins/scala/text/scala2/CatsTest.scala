package org.jetbrains.plugins.scala.text.scala2

import org.jetbrains.plugins.scala.DependencyManagerBase.RichStr
import org.jetbrains.plugins.scala.text.TextToTextTestBase

class CatsTest extends TextToTextTestBase(
  dependencies = Seq(
    "org.typelevel" %% "cats-core" % "2.13.0",
    "org.typelevel" %% "cats-effect" % "3.6.3",
    "org.typelevel" %% "cats-free" % "2.13.0",
    "org.typelevel" %% "cats-laws" % "2.13.0",
  ),
  packages = Seq("cats"),
  minClassCount = 1694,
  classExceptions = Set(
    "cats.effect.Platform", // Cannot resolve org.typelevel.scalaccompat.annotation.static3
    "cats.arrow.FunctionKMacros", // Any
    "cats.arrow.FunctionKMacroMethods", // Any
    "cats.free.FreeFoldStep", // Incorrect types, Tuple2
    "cats.laws.NonEmptyParallelLaws", // Order in type refinement
    "cats.laws.ParallelLaws", // Order in type refinement
    "cats.laws.discipline.NonEmptyParallelTests", // Order in type refinement
    "cats.laws.discipline.ParallelTests", // Order in type refinement
  )
)