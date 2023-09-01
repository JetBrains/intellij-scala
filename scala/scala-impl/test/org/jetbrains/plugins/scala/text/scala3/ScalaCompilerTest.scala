package org.jetbrains.plugins.scala.text.scala3

import org.jetbrains.plugins.scala.text.TextToTextTestBase

class ScalaCompilerTest extends TextToTextTestBase(
  Seq.empty,
  Seq("dotty", "org.scalajs.ir", "scala.quoted.runtime.impl"), Set.empty, 919,
  Set(
    "dotty.tools.backend.jvm.BCodeIdiomatic", // TODO No .type
    "dotty.tools.backend.sjs.ScopedVar", // Cannot resolve AssignmentStackElement (private class)
    "dotty.tools.dotc.ast.Trees", // Extra Nothing type argument in extends
    "dotty.tools.dotc.classpath.JFileDirectoryLookup", // Extra default argument
    "dotty.tools.dotc.core.Definitions", // TODO Unknown type
    "dotty.tools.dotc.core.Denotations", // TODO Parsing error
    "dotty.tools.dotc.core.ExplainingTypeComparer", // Extra default argument
    "dotty.tools.dotc.core.Names", // Extra default argument
    "dotty.tools.dotc.core.OrderingConstraint", // TODO Cannot resolve OrderingConstraint.ParamBounds
    "dotty.tools.dotc.parsing.Parsers", // Extra default argument
    "dotty.tools.dotc.parsing.Scanners", // TODO Enum cases in companion object
    "dotty.tools.dotc.printing.Highlighting", // TODO Parsing error
    "dotty.tools.dotc.printing.PlainPrinter", // Extra default argument
    "dotty.tools.dotc.quoted.PickledQuotes", // TODO Enum cases in companion object
    "dotty.tools.dotc.semanticdb.Scala3", // Order in enum
    "dotty.tools.dotc.transform.LambdaLift", // TODO Unknown type
    "dotty.tools.dotc.transform.init.Semantic", // TODO Semantic.Cache.TreeWrapper is Any
    "dotty.tools.dotc.transform.sjs.JSSymUtils", // TODO Enum cases in companion object
    "dotty.tools.dotc.typer.Applications", // Extra Nothing type argument in extends, different .this qualifier
    "dotty.tools.dotc.typer.OpenSearch", // TODO Parsing error
    "dotty.tools.dotc.typer.ProtoTypes", // Extra default argument
    "dotty.tools.dotc.typer.Synthesizer", // TODO Enum cases in companion object
    "dotty.tools.dotc.util.Chars", // TODO As is \n char constant
    "dotty.tools.dotc.util.GenericHashMap", // Extra default argument
    "dotty.tools.dotc.util.HashSet", // Extra default argument
    "dotty.tools.dotc.util.WeakHashSet", // Extra default argument
  ),
  includeScalaCompiler = true,
  astLoadingFilter = false // TODO Enable
)