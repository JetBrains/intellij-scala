package org.jetbrains.plugins.scala.text.scala2

import org.jetbrains.plugins.scala.DependencyManagerBase.RichStr
import org.jetbrains.plugins.scala.text.TextToTextTestBase

class AmmoniteTest extends TextToTextTestBase(
  Seq(
    "com.lihaoyi" % "ammonite_2.13.16" % "3.0.2",
  ),
  Seq("ammonite"), Set.empty, 156,
  Set(
    "ammonite.compiler.Parsers", // extra space in [_ : ...]
    "ammonite.util.WhiteListClassLoader", // [x0] forSome {type x0 <: _root_.java.lang.Object}
  ),
  includeScalaCompiler = true,
  includeScalaReflect = true
)
