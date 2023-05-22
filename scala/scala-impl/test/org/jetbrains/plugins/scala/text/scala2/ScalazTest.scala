package org.jetbrains.plugins.scala.text.scala2

import org.jetbrains.plugins.scala.DependencyManagerBase.RichStr
import org.jetbrains.plugins.scala.text.TextToTextTestBase

class ScalazTest extends TextToTextTestBase(
  Seq(
    "org.scalaz" %% "scalaz-core" % "7.3.7",
    "org.scalaz" %% "scalaz-effect" % "7.3.7",
  ),
  Seq("scalaz"), Set.empty, 1588,
  Set(
    "scalaz.Foralls", // Excessive parentheses in existential type
    "scalaz.FreeFunctions", // Tuple2
    "scalaz.Heap", // Excessive parentheses in function type
    "scalaz.LanApply", // Any
    "scalaz.std.StringInstances", // No this. prefix for object
    "scalaz.syntax.ToApplicativeErrorOps", // Existential type
    "scalaz.syntax.ToMonadErrorOps", // Existential type
    "scalaz.syntax.ToMonadTellOps", // Existential type
  )
)