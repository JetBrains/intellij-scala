package org.jetbrains.plugins.scala.text

import org.jetbrains.plugins.scala.DependencyManagerBase.RichStr
import org.jetbrains.plugins.scala.base.libraryLoaders.ScalaReflectLibraryLoader
import org.jetbrains.plugins.scala.text.TextToTextTestBase.Library

class TextToTextTest2 extends TextToTextTestBase {
  override protected def isScala3: Boolean = false

  override protected def libraries: Seq[Library] = Seq(
    Library(
      Seq(
        "com.typesafe.akka" %% "akka-actor" % "2.7.0",
        "com.typesafe.akka" %% "akka-http" % "10.5.0",
        "com.typesafe.akka" %% "akka-stream" % "2.7.0", // Provided dependency of akka-http)
      ),
      Seq("akka"), Seq.empty, 2008,
      Seq(
        "akka.dispatch.CachingConfig",
        "akka.dispatch.ExecutorServiceDelegate",
        "akka.http.impl.engine.rendering.HttpResponseRendererFactory",
        "akka.http.impl.engine.server.HttpServerBluePrint",
        "akka.http.scaladsl.server.Directive",
        "akka.http.scaladsl.server.UnsupportedRequestContentTypeRejection",
        "akka.http.scaladsl.server.directives.BasicDirectives",
        "akka.http.scaladsl.unmarshalling.Unmarshaller",
        "akka.pattern.BackoffSupervisor",
        "akka.stream.Supervision",
        "akka.stream.impl.QueueSource",
        "akka.stream.impl.VirtualProcessor",
        "akka.stream.impl.io.ConnectionSourceStage",
        "akka.stream.impl.io.compression.DeflateDecompressor",
        "akka.stream.scaladsl.MergeHub",
        "akka.stream.stage.GraphStageLogic",
      )
    ),

    Library(
      Seq(
        "org.typelevel" %% "cats-core" % "2.8.0",
        "org.typelevel" %% "cats-effect" % "3.3.14",
      ),
      Seq("cats"), Seq.empty, 1330,
      Seq(
        "cats.arrow.FunctionKMacros",
        "cats.arrow.FunctionKMacroMethods",
      ),
    ),

    Library(
      Seq(
        "co.fs2" %% "fs2-core" % "3.6.1",
      ),
      Seq("fs2"), Seq.empty, 56,
      Seq(
        "fs2.Pull",
      ),
    ),

    Library(
      Seq.empty,
      Seq("scala"), Seq("scala.tools", "scala.reflect.quasiquotes", "scala.reflect.reify"), 984,
      Seq(
        "scala.concurrent.impl.Promise",

        "scala.reflect.api.TypeTags",
        "scala.reflect.internal.Chars",
        "scala.reflect.internal.Definitions",
        "scala.reflect.internal.Kinds",
        "scala.reflect.internal.StdNames",
        "scala.reflect.internal.Symbols",
        "scala.reflect.internal.Types",
        "scala.reflect.internal.tpe.CommonOwners",
        "scala.reflect.internal.tpe.FindMembers",
        "scala.reflect.internal.tpe.TypeMaps",
        "scala.reflect.internal.transform.Transforms",
        "scala.reflect.runtime.ReflectionUtils",

        "scala.util.parsing.combinator.PackratParsers",
      ),
    ),

    Library(
      Seq(
        "org.jline" % "jline" % "3.21.0",
      ),
      Seq("scala.tools", "scala.reflect.quasiquotes", "scala.reflect.reify"), Seq.empty, 694,
      Seq(
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
    ),

    Library(
      Seq(
        "org.scalatest" %% "scalatest" % "3.2.14"
      ),
      Seq("org.scalatest"), Seq.empty, 677,
      Seq(
        "org.scalatest.Suite", // Existential type
        "org.scalatest.enablers.InspectorAsserting", // Tuple2 type argument
        "org.scalatest.tools.Framework", // Any
        "org.scalatest.tools.Runner", // Existential type
        "org.scalatest.tools.ScalaTestAntTask", // Cannot resolve reference
        "org.scalatest.tools.ScalaTestFramework", // Any
      ),
    ),

    Library(
      Seq(
        "org.scalaz" %% "scalaz-core" % "7.3.7",
        "org.scalaz" %% "scalaz-effect" % "7.3.7",
      ),
      Seq("scalaz"), Seq.empty, 1588,
      Seq(
        "scalaz.Endomorphic",
        "scalaz.Foralls",
        "scalaz.FreeFunctions",
        "scalaz.Heap",
        "scalaz.LanApply",
        "scalaz.std.StringInstances",
        "scalaz.syntax.ToApplicativeErrorOps",
        "scalaz.syntax.ToMonadErrorOps",
        "scalaz.syntax.ToMonadTellOps",
      ),
    ),

    Library(
      Seq(
        "dev.zio" %% "zio" % "2.0.2",
        "dev.zio" %% "zio-streams" % "2.0.2",
      ),
      Seq("zio"), Seq.empty, 226,
      Seq.empty,
    ),
  )

  override protected val includeCompilerAsLibrary = true

  override def librariesLoaders = super.librariesLoaders :+ ScalaReflectLibraryLoader
}
