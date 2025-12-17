package org.jetbrains.plugins.scala.text.scala2

import org.jetbrains.plugins.scala.DependencyManagerBase.RichStr
import org.jetbrains.plugins.scala.text.TextToTextTestBase

class MillTest extends TextToTextTestBase(
  dependencies = Seq(
    "com.lihaoyi" %% "mill-main" % "0.12.15",
  ),
  packages = Seq("mill"),
  minClassCount = 140,
  classExceptions = Set(
    "mill.api.AggWrapper", // AggWrapper.this.
    "mill.resolve.ExpandBraces", // private trait ExpandBraces.Fragment
  ),
  includeScalaReflect = true
)