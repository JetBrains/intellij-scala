package org.jetbrains.plugins.scala.text.scala3

import org.jetbrains.plugins.scala.DependencyManagerBase.RichStr
import org.jetbrains.plugins.scala.text.TextToTextTestBase

class DoobieTest extends TextToTextTestBase(
  Seq(
    "org.tpolecat" %% "doobie-core" % "1.0.0-RC1",
  ),
  Seq("doobie"), Set.empty, 117,
  Set(
    "doobie.util.GetPlatform", // Cannot resolve p.MirroredElemTypes, scala.EmptyTuple
    "doobie.util.PutPlatform", // Cannot resolve m.MirroredElemTypes, scala.EmptyTuple
    "doobie.util.ReadPlatform", // EmptyTuple is Any
    "doobie.util.WritePlatform", // EmptyTuple is Any
  )
)