package org.jetbrains.plugins.scala.text.scala2

import org.jetbrains.plugins.scala.DependencyManagerBase.RichStr
import org.jetbrains.plugins.scala.text.TextToTextTestBase

class ScalaCompilerTest extends TextToTextTestBase(
  Seq(
    "org.jline" % "jline" % "3.21.0",
  ),
  Seq("scala.tools", "scala.reflect.quasiquotes", "scala.reflect.reify"), Set.empty, 694,
  Set(
    "scala.tools.nsc.Global", // Reference to object without this. prefix
    "scala.tools.nsc.InterpreterLoop", // Standalone annotation
    "scala.tools.nsc.PipelineMainClass", // Any
    "scala.tools.nsc.ast.NodePrinters", // Reference to object without this. prefix
    "scala.tools.nsc.ast.TreeDSL", // Reference to object without this. prefix
    "scala.tools.nsc.ast.parser.Parsers", // Reference to object without this. prefix
    "scala.tools.nsc.backend.jvm.opt.Inliner", // Reference to object without this. prefix
    "scala.tools.nsc.doc.html.HtmlPage", // Reference to object without this. prefix
    "scala.tools.nsc.interpreter.Power", // _1 type argument
    "scala.tools.nsc.interpreter.shell.ILoop", // Reference to object without this. prefix
    "scala.tools.nsc.interpreter.shell.ShellConfig", // Order in type refinement
    "scala.tools.nsc.settings.ScalaSettings", // $1
    "scala.tools.nsc.settings.Warnings", // _1.`type`
    "scala.tools.nsc.symtab.SymbolLoaders", // Reference to object without this. prefix
    "scala.tools.nsc.symtab.classfile.ClassfileParser", // Reference to object without this. prefix
    "scala.tools.nsc.tasty.bridge.AnnotationOps", // Different .this qualifier
    "scala.tools.nsc.tasty.bridge.FlagOps", // Reference to object without this. prefix
    "scala.tools.nsc.tasty.bridge.TypeOps", // Different .this qualifier
    "scala.tools.nsc.transform.patmat.Logic", // Reference to object without this. prefix
    "scala.tools.nsc.transform.patmat.MatchApproximation", // Reference to object without this. prefix
    "scala.tools.nsc.transform.patmat.MatchTreeMaking", // No _root_ qualifier
    "scala.tools.nsc.typechecker.Analyzer", // Reference to object without this. prefix
    "scala.tools.nsc.typechecker.AnalyzerPlugins", // Cannot resolve reference
    "scala.tools.nsc.typechecker.ContextErrors", // No _root_ qualifier
    "scala.tools.nsc.typechecker.Implicits", // Cannot resolve reference
    "scala.tools.nsc.typechecker.Namers", // Different .this qualifier
    "scala.tools.nsc.typechecker.TreeCheckers", // Reference to object without this. prefix
    "scala.tools.nsc.typechecker.TypeDiagnostics", // Cannot resolve reference
    "scala.tools.nsc.typechecker.Typers", // Different .this qualifier
    "scala.tools.nsc.typechecker.Unapplies", // Reference to object without this. prefix
    "scala.tools.nsc.typechecker.splain.SplainErrors", // Cannot resolve reference
    "scala.tools.nsc.util.WorkScheduler", // Excessive parentheses in function type
    "scala.tools.reflect.FormatInterpolator", // Reference to object without this. prefix
    "scala.tools.reflect.WrappedProperties", // Existential type
    "scala.reflect.quasiquotes.Parsers", // Reference to object without this. prefix
  ),
  includeScalaReflect = true,
  includeScalaCompiler = true
)