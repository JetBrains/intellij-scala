package org.jetbrains.plugins.scala.text.scala2

import org.jetbrains.plugins.scala.DependencyManagerBase.RichStr
import org.jetbrains.plugins.scala.text.TextToTextTestBase

class MillTest extends TextToTextTestBase(
  Seq(
    "com.lihaoyi" %% "mill-main" % "0.12.15",
  ),
  Seq("mill"), Set.empty, 140,
  Set(
    "mill.api.AggWrapper", // AggWrapper.this.
    "mill.api.Ctx", // IndexedSeq[`?$2`] forSome {type `?$2`}
    "mill.api.JsonFormatters", // Enum[`?$1`] forSome {type `?$1`}
    "mill.eval.CodeSigUtils", // Class[`?$4`] forSome {type `?$4`}
    "mill.resolve.ExpandBraces", // private trait ExpandBraces.Fragment
  ),
  includeScalaReflect = true
)