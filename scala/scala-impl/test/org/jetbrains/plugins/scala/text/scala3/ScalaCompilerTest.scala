package org.jetbrains.plugins.scala.text.scala3

import org.jetbrains.plugins.scala.text.TextToTextTestBase

class ScalaCompilerTest extends TextToTextTestBase(
  Seq.empty,
  Seq("dotty", "org.scalajs.ir", "scala.quoted.runtime.impl"), Set.empty, 919,
  Set(
    "dotty.tools.backend.jvm.BCodeIdiomatic", // TODO No .type
    "dotty.tools.backend.sjs.ScopedVar", // Cannot resolve AssignmentStackElement (private class)
    "dotty.tools.dotc.ast.Trees", // Extra Nothing type argument in extends
    "dotty.tools.dotc.core.Definitions", // TODO Unknown type
    "dotty.tools.dotc.core.Denotations", // TODO Parsing error
    "dotty.tools.dotc.core.OrderingConstraint", // TODO Cannot resolve OrderingConstraint.ParamBounds
    "dotty.tools.dotc.parsing.Scanners", // TODO Enum cases in companion object
    "dotty.tools.dotc.printing.Highlighting", // TODO Parsing error
    "dotty.tools.dotc.quoted.PickledQuotes", // TODO Enum cases in companion object
    "dotty.tools.dotc.semanticdb.Scala3", // Order in enum
    "dotty.tools.dotc.transform.LambdaLift", // TODO Unknown type
    "dotty.tools.dotc.transform.init.Semantic", // TODO Semantic.Cache.TreeWrapper is Any
    "dotty.tools.dotc.transform.sjs.JSSymUtils", // TODO Enum cases in companion object
    "dotty.tools.dotc.typer.Applications", // Extra Nothing type argument in extends, different .this qualifier
    "dotty.tools.dotc.typer.OpenSearch", // TODO Parsing error
    "dotty.tools.dotc.typer.Synthesizer", // TODO Enum cases in companion object
  ),
  includeScalaCompiler = true,
  astLoadingFilter = false // TODO Enable
)