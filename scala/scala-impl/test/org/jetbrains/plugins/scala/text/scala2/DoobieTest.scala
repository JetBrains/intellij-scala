package org.jetbrains.plugins.scala.text.scala2

import org.jetbrains.plugins.scala.DependencyManagerBase.RichStr
import org.jetbrains.plugins.scala.text.TextToTextTestBase

class DoobieTest extends TextToTextTestBase(
  Seq(
    "org.tpolecat" %% "doobie-core" % "1.0.0-RC1",
  ),
  Seq("doobie"), Set.empty, 122,
  Set(
    "doobie.util.EvenLower", // Excessive parentheses in existential type
    "doobie.util.EvenLowerPriorityWrite", // Excessive parentheses in existential type
  ),
  includeScalaReflect = true
)