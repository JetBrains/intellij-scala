package org.jetbrains.plugins.scala.text.scala2

import org.jetbrains.plugins.scala.DependencyManagerBase.RichStr
import org.jetbrains.plugins.scala.text.TextToTextTestBase

class DoobieTest extends TextToTextTestBase(
  dependencies = Seq(
    "org.tpolecat" %% "doobie-core" % "1.0.0-RC1",
  ),
  packages = Seq("doobie"),
  minClassCount = 122,
  classExceptions = Set(
    "doobie.util.EvenLower", // Excessive parentheses in existential type
    "doobie.util.EvenLowerPriorityWrite", // Excessive parentheses in existential type
  ),
  includeScalaReflect = true
)