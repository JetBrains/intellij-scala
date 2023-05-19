package org.jetbrains.plugins.scala.text

import org.jetbrains.plugins.scala.DependencyManagerBase.RichStr
import org.jetbrains.plugins.scala.base.libraryLoaders.ScalaReflectLibraryLoader
import org.jetbrains.plugins.scala.text.TextToTextTestBase.Library

class TextToTextTest2 extends TextToTextTestBase {
  override protected def isScala3: Boolean = false

  override protected val includeCompilerAsLibrary = true

  override def librariesLoaders = super.librariesLoaders :+ ScalaReflectLibraryLoader

  override protected def libraries: Seq[Library] = Seq(
    Library(
      Seq(
        "com.typesafe.akka" %% "akka-actor" % "2.7.0",
        "com.typesafe.akka" %% "akka-actor-typed" % "2.7.0",
        "com.typesafe.akka" %% "akka-cluster" % "2.7.0",
        "com.typesafe.akka" %% "akka-http" % "10.5.0",
        "com.typesafe.akka" %% "akka-persistence" % "2.7.0",
        "com.typesafe.akka" %% "akka-stream" % "2.7.0",
      ),
      Seq("akka"), Seq("akka.persistence.journal.leveldb", "akka.remote.artery.aeron", "akka.remote.transport.netty") /* External references */, 2628,
      Seq(
        "akka.dispatch.CachingConfig", // Existential type
        "akka.dispatch.ExecutorServiceDelegate", // Existential type
        "akka.http.impl.engine.rendering.HttpResponseRendererFactory", // No this. prefix for object
        "akka.http.impl.engine.server.HttpServerBluePrint", // Order in type refinement
        "akka.http.scaladsl.server.Directive", // By-name function type parameter
        "akka.http.scaladsl.server.directives.BasicDirectives", // Excessive parentheses in function type
        "akka.stream.Supervision", // Excessive parentheses in compound type
        "akka.stream.impl.QueueSource", // Order in type refinement
        "akka.stream.impl.VirtualProcessor", // No this. prefix for object
        "akka.stream.impl.io.ConnectionSourceStage", // Order in type refinement
        "akka.stream.impl.io.compression.DeflateDecompressor", // inflating.type is Any
        "akka.stream.scaladsl.MergeHub", // Cannot resolve Event
        "akka.stream.stage.GraphStageLogic", // Excessive parentheses in function type
      )
    ),

    Library(
      Seq(
        "org.typelevel" %% "cats-core" % "2.8.0",
        "org.typelevel" %% "cats-effect" % "3.3.14",
        "org.typelevel" %% "cats-free" % "2.8.0",
        "org.typelevel" %% "cats-laws" % "2.8.0",
      ),
      Seq("cats"), Seq.empty, 1520,
      Seq(
        "cats.arrow.FunctionKMacros", // Any
        "cats.arrow.FunctionKMacroMethods", // Any
        "cats.free.FreeFoldStep", // Incorrect types, Tuple2
        "cats.laws.NonEmptyParallelLaws", // Order in type refinement
        "cats.laws.ParallelLaws", // Order in type refinement
        "cats.laws.discipline.NonEmptyParallelTests", // Order in type refinement
        "cats.laws.discipline.ParallelTests", // Order in type refinement
      )
    ),

    Library(
      Seq(
        "co.fs2" %% "fs2-core" % "3.6.1",
      ),
      Seq("fs2"), Seq.empty, 56,
      Seq(
        "fs2.Pull", // Any
      )
    ),

  Library(
      Seq(
        "com.typesafe.play" %% "play" % "2.8.19",
      ),
      Seq("controllers", "models", "play", "views"), Seq.empty, 605,
      Seq(
        "controllers.AssetsModule", // _1
        "play.api.i18n.I18nModule", // I18nModule.this._1
        "views.html.helper.form", // By-name function type parameter
        "views.html.helper.script", // By-name function type parameter
        "views.html.helper.style", // By-name function type parameter
      )
    ),

    Library(
      Seq(
        "org.tpolecat" %% "doobie-core" % "1.0.0-RC1",
      ),
      Seq("doobie"), Seq.empty, 122,
      Seq(
        "doobie.util.EvenLower", // Excessive parentheses in existential type
        "doobie.util.EvenLowerPriorityWrite", // Excessive parentheses in existential type
      )
    ),

    Library(
      Seq(
        "io.getquill" %% "quill-sql" % "4.6.0",
        "io.getquill" %% "quill-jdbc-zio" % "4.6.0"
      ),
      Seq("io.getquill"), Seq.empty, 505,
      Seq(
        "io.getquill.EntityQuery", // No parentheses for repeated function type
        "io.getquill.EntityQueryModel", // No parentheses for repeated function type
        "io.getquill.Insert", // No parentheses for repeated function type
        "io.getquill.ast.EqualityOperator", // No case object _==
        "io.getquill.context.jdbc.Decoders", // Decoders.this. vs JdbcContextTypes.this.
        "io.getquill.context.jdbc.Encoders", // Encoders.this. vs JdbcContextTypes.this.
        "io.getquill.dsl.DynamicQueryDsl", // DynamicQueryDsl. vs CoreDsl.this.
        "io.getquill.dsl.MetaDsl", // No parentheses for repeated function type
        "io.getquill.dsl.QueryDsl", // No parentheses for repeated function type
        "io.getquill.norm.SheathLeafClauses", // Cannot resolve reference
        "io.getquill.util.EnableReflectiveCalls", // `<refinement>` is Any
      )
    ),

    Library(
      Seq.empty,
      Seq("scala"), Seq("scala.tools", "scala.reflect.quasiquotes", "scala.reflect.reify"), 984,
      Seq(
        "scala.concurrent.impl.Promise", // Function1

        "scala.reflect.api.TypeTags", // TypeTags.this. vs Universe.this
        "scala.reflect.internal.Chars", // No \n char
        "scala.reflect.internal.Definitions", // type NameTypeDefinitions.this.TermName in type refinement
        "scala.reflect.internal.Kinds", // No this. prefix for object
        "scala.reflect.internal.StdNames", // No this. prefix for object
        "scala.reflect.internal.Symbols", // Symbols.this. vs SymbolTable.this.
        "scala.reflect.internal.Types", // Typs.this. vs SymbolTable.this.
        "scala.reflect.internal.tpe.CommonOwners", // CommonOwners.this. vs SymbolTable.this.
        "scala.reflect.internal.tpe.FindMembers", // Cannot resolve reference
        "scala.reflect.internal.tpe.TypeMaps", // TypeMaps.this. vs SymbolTable.this.
        "scala.reflect.internal.transform.Transforms", // $1, _1, cannot resolve reference
        "scala.reflect.runtime.ReflectionUtils", // Existential type

        "scala.util.parsing.combinator.PackratParsers", // Any, cannot resolve reference
      )
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
      )
    ),

    Library(
      Seq(
        "org.scalacheck" %% "scalacheck" % "1.17.0",
      ),
      Seq("org.scalacheck"), Seq.empty, 38,
      Seq.empty
    ),

    Library(
      Seq(
        "org.scalactic" %% "scalactic" % "3.2.14",
      ),
      Seq("org.scalactic"), Seq.empty, 170,
      Seq(
        "org.scalactic.Accumulation", // No parentheses for repeated function type
        "org.scalactic.FutureSugar", // No parentheses for repeated function type
        "org.scalactic.TrySugar", // No parentheses for repeated function type
        "org.scalactic.source.TypeInfoMacro", // Cannot resolve reference
      )
    ),

    Library(
      Seq(
        "org.scalatest" %% "scalatest" % "3.2.14"
      ),
      Seq("org.scalatest"), Seq.empty, 677,
      Seq(
        "org.scalatest.Suite", // Existential type
        "org.scalatest.enablers.InspectorAsserting", // Tuple2 type argument
//        "org.scalatest.tools.Framework", // Any
        "org.scalatest.tools.Runner", // Existential type
        "org.scalatest.tools.ScalaTestAntTask", // Cannot resolve reference
//        "org.scalatest.tools.ScalaTestFramework", // Any
      )
    ),

    Library(
      Seq(
        "org.scalaz" %% "scalaz-core" % "7.3.7",
        "org.scalaz" %% "scalaz-effect" % "7.3.7",
      ),
      Seq("scalaz"), Seq.empty, 1588,
      Seq(
        "scalaz.Foralls", // Excessive parentheses in existential type
        "scalaz.FreeFunctions", // Tuple2
        "scalaz.Heap", // Excessive parentheses in function type
        "scalaz.LanApply", // Any
        "scalaz.std.StringInstances", // No this. prefix for object
        "scalaz.syntax.ToApplicativeErrorOps", // Existential type
        "scalaz.syntax.ToMonadErrorOps", // Existential type
        "scalaz.syntax.ToMonadTellOps", // Existential type
      )
    ),

    Library(
      Seq(
        "dev.zio" %% "zio" % "2.0.2",
        "dev.zio" %% "zio-streams" % "2.0.2",
      ),
      Seq("zio"), Seq.empty, 226,
      Seq.empty
    ),
  )
}
