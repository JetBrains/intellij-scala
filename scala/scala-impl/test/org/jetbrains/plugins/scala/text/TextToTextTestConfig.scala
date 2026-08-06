package org.jetbrains.plugins.scala.text

import org.jetbrains.plugins.scala.corpus.{CorpusProjects, ProjectCorpusTestDef}
import org.jetbrains.plugins.scala.text.TextToTextTestBase.Content

private case class TextToTextTestConfig(
  packageExceptions: Set[String] = Set.empty,
  minClassCount: Int,
  classExceptions: Set[String] = Set.empty,
  withSources: Boolean = false,
  classesWithoutSource: Set[String] = Set.empty,
  sourceExceptions: Set[String] = Set.empty,
  astLoadingFilter: Boolean = true,
  transformed: (Content, String) => String = (_, s) => s,
  aliasJava: Boolean = true,
  aliasScala: Boolean = true,
)

private object TextToTextTestConfig {
  def default: TextToTextTestConfig = TextToTextTestConfig(
    minClassCount = 0,
  )

  val projectConfigs: Map[ProjectCorpusTestDef, TextToTextTestConfig] = Map(
    // Akka
    CorpusProjects.Akka.scala2 -> TextToTextTestConfig(
      packageExceptions = Set("akka.persistence.journal.leveldb", "akka.remote.artery.aeron", "akka.remote.transport.netty"),
      minClassCount = 2567,
      classExceptions = Set(
        "akka.dispatch.CachingConfig", // Existential type
        "akka.dispatch.ExecutorServiceDelegate", // Existential type
        "akka.http.impl.engine.rendering.HttpResponseRendererFactory", // No this. prefix for object
        "akka.http.impl.engine.server.HttpServerBluePrint", // Order in type refinement
        "akka.http.scaladsl.server.Directive", // By-name function type parameter
        "akka.stream.Supervision", // Excessive parentheses in compound type
        "akka.stream.impl.QueueSource", // Order in type refinement
        "akka.stream.impl.VirtualProcessor", // No this. prefix for object
        "akka.stream.impl.io.ConnectionSourceStage", // Order in type refinement
        "akka.stream.impl.io.compression.DeflateDecompressor", // inflating.type is Any
        "akka.stream.scaladsl.MergeHub", // Cannot resolve Event
      ),
    ),
    CorpusProjects.Akka.scala3 -> TextToTextTestConfig(
      packageExceptions = Set("akka.parboiled2", "akka.persistence.journal.leveldb", "akka.remote.artery.aeron", "akka.remote.transport.netty"),
      minClassCount = 2521,
      classExceptions = Set(
        "akka.actor.typed.internal.receptionist.Platform", // Match type case without qualifier
        "akka.http.impl.model.parser.CommonRules", // HList type reduction
        "akka.http.impl.model.parser.SimpleHeaders", // HList type reduction
        "akka.http.impl.util.JavaMapping", // Cannot resolve S, J
        "akka.http.scaladsl.server.Directive", // By-name function type parameter, SCL-21149
        "akka.http.scaladsl.server.util.BinaryPolyFunc", // Unknown
        "akka.stream.scaladsl.MergeHub", // private method references private class (skip private[OuterClass] methods?)
      )
    ),

    // Ammonite
    CorpusProjects.Ammonite.scala2 -> TextToTextTestConfig(
      minClassCount = 156,
      classExceptions = Set(
        "ammonite.compiler.Parsers", // extra space in [_ : ...]
        "ammonite.util.WhiteListClassLoader", // [x0] forSome {type x0 <: _root_.java.lang.Object}
      ),
    ),
    CorpusProjects.Ammonite.scala3 -> TextToTextTestConfig(
      minClassCount = 161,
      classExceptions = Set(
        "ammonite.repl.Repl", // Unknown vs Any
      )
    ),

    // Cats
    CorpusProjects.Cats.scala2 -> TextToTextTestConfig(
      minClassCount = 1694,
      classExceptions = Set(
        "cats.effect.Platform", // Cannot resolve org.typelevel.scalaccompat.annotation.static3
        "cats.arrow.FunctionKMacros", // Any
        "cats.arrow.FunctionKMacroMethods", // Any
        "cats.free.FreeFoldStep", // Incorrect types, Tuple2
        "cats.laws.NonEmptyParallelLaws", // Order in type refinement
        "cats.laws.ParallelLaws", // Order in type refinement
        "cats.laws.discipline.NonEmptyParallelTests", // Order in type refinement
        "cats.laws.discipline.ParallelTests", // Order in type refinement
      )
    ),
    CorpusProjects.Cats.scala3 -> TextToTextTestConfig(
      minClassCount = 1693,
      classExceptions = Set(
        "cats.effect.Platform", // Cannot resolve _root_.org.typelevel.scalaccompat.annotation.static3
        "cats.laws.NonEmptyParallelLaws", // Order in type refinement
        "cats.laws.ParallelLaws", // Order in type refinement
        "cats.laws.discipline.NonEmptyParallelTests", // Order in type refinement
        "cats.laws.discipline.ParallelTests", // Order in type refinement
      )
    ),

    // Circe
    CorpusProjects.Circe.scala2 -> TextToTextTestConfig(
      minClassCount = 79,
      classExceptions = Set(
        "io.circe.Encoder", // export (correct, see ScalaNamesValidator)
        "io.circe.LowPriorityDecoders", // export (correct, see ScalaNamesValidator)
        "io.circe.LowPriorityEncoders", // export (correct, see ScalaNamesValidator)
        "io.circe.generic.AutoDerivation", // export (correct, see ScalaNamesValidator)
        "io.circe.generic.Deriver", // Cannot resolve reference
        "io.circe.generic.GenericJsonCodecMacros", // Cannot resolve reference
        "io.circe.generic.util.macros.DerivationMacros", // Cannot resolve reference
        "io.circe.generic.util.macros.ExportMacros", // Cannot resolve reference
        "io.circe.generic.util.macros.JsonCodecMacros", // Cannot resolve reference
      )
    ),
    CorpusProjects.Circe.scala3 -> TextToTextTestConfig(
      minClassCount = 87,
      withSources = true,
      classesWithoutSource = Set(
        // Why are sources not found for these classes?
        "io.circe.ProductCodecs",
        "io.circe.ProductDecoders",
        "io.circe.ProductEncoders",
        "io.circe.TupleDecoders",
        "io.circe.TupleEncoders",
      )
    ),

    // Doobie
    CorpusProjects.Doobie.scala2 -> TextToTextTestConfig(
      minClassCount = 136,
    ),
    CorpusProjects.Doobie.scala3 -> TextToTextTestConfig(
      minClassCount = 124,
      classExceptions = Set(
        "doobie.util.ReadPlatform", // _root_.scala.EmptyTuple | _root_.scala.Any
        "doobie.util.WritePlatform" // _root_.scala.EmptyTuple | _root_.scala.Any
      )
    ),

    // FS2
    CorpusProjects.Fs2.scala2 -> TextToTextTestConfig(
      minClassCount = 72,
      classExceptions = Set(
        "fs2.Pull", // Any
        "fs2.interop.flow.StreamSubscriber", // Cannot resolve fs2.interop.flow.StreamSubscriber (in private object)
      )
    ),
    CorpusProjects.Fs2.scala3 -> TextToTextTestConfig(
      minClassCount = 70,
      classExceptions = Set(
        "fs2.Chunk", // Cannot resolve @org.typelevel.scalaccompat.annotation.internal.nowarnIgnored
        "fs2.ChunkCompanionPlatform", // IArray is Any
        "fs2.ChunkPlatform", // IArray is Any
        "fs2.CollectorPlatform", // type.Aux
        "fs2.Pull", // fs2.Pull.Terminal is Any
        "fs2.Stream", // Cannot resolve @org.typelevel.scalaccompat.annotation.internal.nowarnIgnored
        "fs2.interop.flow.StreamSubscriber", // Cannot resolve fs2.interop.flow.StreamSubscriber (in private object)
      ),
      withSources = true,
      sourceExceptions = Set(
        "fs2.Stream", // private type ZipWithLeft
      )
    ),

    // Jsoniter
    CorpusProjects.Jsoniter.scala2 -> TextToTextTestConfig(
      minClassCount = 15
    ),
    CorpusProjects.Jsoniter.scala3 -> TextToTextTestConfig(
      minClassCount = 23,
      withSources = true
    ),

    // Mill
    CorpusProjects.Mill.scala2 -> TextToTextTestConfig(
      minClassCount = 140,
      classExceptions = Set(
        "mill.api.AggWrapper", // AggWrapper.this.
        "mill.resolve.ExpandBraces", // private trait ExpandBraces.Fragment
      ),
    ),
    CorpusProjects.Mill.scala3 -> TextToTextTestConfig(
      minClassCount = 134,
      withSources = true,
      sourceExceptions = Set(
        "mill.api.Result", // with Product with Serializable
        "mill.define.Command", // extends Task[T] vs Task
        "mill.define.InputImpl", // immutable.Seq[_root_.mill.define.Task[?]] vs Nil.type
        "mill.define.TargetImpl", // extends Task[T] vs Task
        "mill.define.Worker", // extends Task[T] vs Task
        "mill.define.internal.Cacher", // x$1.reflect.Symbol vs Any
        "mill.define.internal.CacherMacros", // Cannot resolve TypeRepr
        "mill.define.internal.CrossMacros", // Cannot resolve TypeRepr
        "mill.define.internal.ShimService", // Quotes
        "mill.main.VisualizeModule", // private type
      ),
      transformed = {
        case (Content.DecompiledVsSourceOutline, s) =>
          s.replaceAll(" *@_root_.mill.moduledefs.Scaladoc\\(.*?\\)\n", "")
        case (_, s) => s
      }
    ),

    // Play
    CorpusProjects.Play.scala2 -> TextToTextTestConfig(
      minClassCount = 624,
      classExceptions = Set(
        "views.html.helper.form", // By-name function type parameter
        "views.html.helper.script", // By-name function type parameter
        "views.html.helper.style", // By-name function type parameter
        "play.api.libs.json.DefaultReads", // Enum
        "play.api.libs.json.Json", // Enum
      ),
    ),
    CorpusProjects.Play.scala3 -> TextToTextTestConfig(
      minClassCount = 628,
      classExceptions = Set(
        "play.api.mvc.ActionBuilder", // Extra [Nothing] type argument
        "play.api.mvc.DefaultActionBuilderImpl", // Extra [Nothing] type argument
        "play.api.mvc.DefaultMessagesActionBuilderImpl", // Extra [Nothing] type argument
        "views.html.helper.form", // By-name function type parameter
        "views.html.helper.script", // By-name function type parameter
        "views.html.helper.style", // By-name function type parameter
      )
    ),

    // Quill
    CorpusProjects.Quill.scala2 -> TextToTextTestConfig(
      minClassCount = 508,
      classExceptions = Set(
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
      ),
    ),
    CorpusProjects.Quill.scala3 -> TextToTextTestConfig(
      minClassCount = 661,
      classExceptions = Set(
        "io.getquill.DynamicInsert", // No parentheses for repeated function type
        "io.getquill.EntityQueryModel", // No parentheses for repeated function type
        "io.getquill.InfixInterpolator", // Top-level definitions
        "io.getquill.InjectableEagerPlanter", // Function1
        "io.getquill.Insert", // No parentheses for repeated function type
        "io.getquill.SqlInfixInterpolator", // Top-level definitions
        "io.getquill.ToDynamicAction", // Top-level definitions
        "io.getquill.ToDynamicActionReturning", // Top-level definitions
        "io.getquill.ToDynamicEntityQuery", // Top-level definitions
        "io.getquill.ToDynamicInsert", // Top-level definitions
        "io.getquill.ToDynamicQuery", // Top-level definitions
        "io.getquill.ToDynamicUpdate", // Top-level definitions
        "io.getquill.ast.EqualityOperator", // No case object _==
        "io.getquill.context.InsertUpdateMacro", // Enum
        "io.getquill.context.LiftMacro", // Function1
        "io.getquill.context.jdbc.Decoders", // Decoders.this vs JdbcContextTypes.this
        "io.getquill.context.jdbc.Encoders", // Encoders.this vs JdbcContextTypes.this
        "io.getquill.metaprog.InjectableEagerPlanterExpr", // Function1
        "io.getquill.norm.SheathLeafClauses", // Cannot resolve reference
        "io.getquill.parser.ParserHelpers", // TODO using(x: Int, Long)
        "io.getquill.quat.QuatMakingBase", // $1n in extension, SCL-21551
      ),
      withSources = true,
      classesWithoutSource = Set(
        "io.getquill.util.testLoad"
      ),
      sourceExceptions = Set(
        "io.getquill.IdiomContext", // scala.Option | scala.Some
        "io.getquill.MirrorContextBase", // MirrorContextBase.this.QueryMirror | MirrorContextBase.this.RunQueryResult
        "io.getquill.ast.StatefulTransformerWithStack", // scala.Option | scala.None.type
        "io.getquill.context.Execution", // scala.quoted.Expr | scala.Any
        "io.getquill.context.ExtractLifts", // [?, ?, ?] | [_$1, _$2, _$3]]) forSome {type _$1; type _$2; type _$3}]
        "io.getquill.context.Extraction", // Extraction.Simple | Extraction with Extraction.Simple
        "io.getquill.context.Particularize", // ?=> | N/A
        "io.getquill.context.PrepareDynamicExecution", // [ResultRow, Session, T] | [scala.Nothing, scala.Nothing, T]
        "io.getquill.context.QueryExecution", // quoted.Expr[Res] | Res
        "io.getquill.context.QueryExecutionBatch", // ... | Cannot define expected type
        "io.getquill.context.ReflectivePathChainLookup", // scala.Option[java.lang.Object] | scala.Any
        "io.getquill.context.StaticState", // [?, ?, ?] | [_$1, _$2, _$3]]) forSome {type _$1; type _$2; type _$3}]
        "io.getquill.context.StaticSpliceMacro", // scala.Boolean | scala.Any
        "io.getquill.dsl.InfixDsl", // \" | " (in annotation)
        "io.getquill.generic.DeconstructElaboratedEntityLevels", // Multiple scala.quoted.Exp
        "io.getquill.generic.ConstructDecoded", // scala.quoted.Exp | scala.Any
        "io.getquill.generic.ConstructType", // scala.quoted.Exp | scala.Any
        "io.getquill.generic.ElaborateStructure", // scala.quoted.Exp | scala.Any
        "io.getquill.generic.GenericDecoder", // scala.quoted.Exp | scala.Any
        "io.getquill.jdbczio.Quill", // io.getquill.context.qzio.ZioJdbcContext[_root_.io.getquill.PostgresDialect, N] | io.getquill.PostgresZioJdbcContext[N]
        "io.getquill.metaprog.Extractors", // scala.quoted.Exp | scala.Any
        "io.getquill.metaprog.Is", // scala.quoted.Exp | scala.Nothing
        "io.getquill.metaprog.QuotationLotExpr", // Type inference discrepancies
        "io.getquill.metaprog.TranspileConfigLiftable", // ?=> | N/A
        "io.getquill.metaprog.etc.ListFlicer", // scala.quoted.Exp | scala.Any
        "io.getquill.norm.AdHocReduction", // | vs with
        "io.getquill.norm.ExpandReturning", // N/A | with Product with Serializable
        "io.getquill.norm.OrderTerms", // | vs with
        "io.getquill.norm.SymbolicReduction", // | vs with
        "io.getquill.parser.ActionParser", // ?=> | N/A
        "io.getquill.parser.BatchActionParser", // ?=> | N/A
        "io.getquill.parser.BlockParser", // ?=> | N/A
        "io.getquill.parser.CasePatMatchParser", // ?=> | N/A
        "io.getquill.parser.ComplexValueParser", // ?=> | N/A
        "io.getquill.parser.ExtrasParser", // ?=> | N/A
        "io.getquill.parser.FunctionApplyParser", // ?=> | N/A
        "io.getquill.parser.FunctionParser", // ?=> | N/A
        "io.getquill.parser.GenericExpressionsParser", // ?=> | N/A
        "io.getquill.parser.IfElseParser", // ?=> | N/A
        "io.getquill.parser.InfixParser", // ?=> | N/A
        "io.getquill.parser.Lifter", // ?=> | N/A
        "io.getquill.parser.OperationsParser", // ?=> | N/A
        "io.getquill.parser.OptionParser", // ?=> | N/A
        "io.getquill.parser.QueryParser", // ?=> | N/A
        "io.getquill.parser.QueryScalarsParser", // ?=> | N/A
        "io.getquill.parser.QuotationParser", // ?=> | N/A
        "io.getquill.parser.SerialHelper", // ?=> | N/A
        "io.getquill.parser.SetOperationsParser", // ?=> | N/A
        "io.getquill.parser.TraversableOperationParser", // ?=> | N/A
        "io.getquill.parser.Unlifter", // ?=> | N/A
        "io.getquill.parser.ValParser", // ?=> | N/A
        "io.getquill.parser.ValueParser", // ?=> | N/A
        "io.getquill.sql.norm.QueryLevel", // N/A | with Product with Serializable
        "io.getquill.util.Format", // java.lang.Object | java.io.Serializable
        "io.getquill.util.Interpolator2", // scala.|[T, (T, L)] | scala.Any
        "io.getquill.util.Load", // private | private[Load]
      ),
      transformed = {
        case (Content.DecompiledVsSourceOutline, s) =>
          s.replaceAll("@_root_\\.scala\\.annotation\\.compileTimeOnly.*\\s+", "") // Reference as annotation argument
            .replaceAll("x\\$\\d: _root_.scala.quoted.Quotes", "_root_.scala.quoted.Quotes") // SCL-21552
            .replaceAll("x\\$\\d.reflect", "_root_.scala.quoted.quotes.reflect") // SCL-21552
            .replace(" <: _root_.scala.AnyKind", "") // SCL-21240
        case (Content.SourceOutline, s) =>
          s.replaceAll("@_root_\\.scala\\.annotation\\.compileTimeOnly.*\\s+", "") // Reference as annotation argument
        case (_, s) => s
      }
    ),

    // Scalacheck
    CorpusProjects.Scalacheck.scala2 -> TextToTextTestConfig(
      minClassCount = 38
    ),
    CorpusProjects.Scalacheck.scala3 -> TextToTextTestConfig(
      minClassCount = 39,
      withSources = true,
      sourceExceptions = Set(
        "org.scalacheck.Gen", // private type | N/A
        "org.scalacheck.Properties", // mutable.ListBuffer[String, Prop)] | Properties.this.props.type
        "org.scalacheck.commands.Commands", // private type | N/A
      )
    ),

    // ScalaCompiler
    CorpusProjects.ScalaCompiler.scala2 -> TextToTextTestConfig(
      minClassCount = 710,
      classExceptions = Set(
        "scala.tools.nsc.CompilationUnits", // Reference to object without this. prefix
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
        "scala.tools.nsc.typechecker.Contexts", // Cannot resolve reference to Contexts.this.global.Position
        "scala.tools.nsc.typechecker.Implicits", // Cannot resolve reference
        "scala.tools.nsc.typechecker.Namers", // Different .this qualifier
        "scala.tools.nsc.typechecker.TreeCheckers", // Reference to object without this. prefix
        "scala.tools.nsc.typechecker.TypeDiagnostics", // Cannot resolve reference
        "scala.tools.nsc.typechecker.Typers", // Different .this qualifier
        "scala.tools.nsc.typechecker.Unapplies", // Reference to object without this. prefix
        "scala.tools.nsc.typechecker.splain.SplainErrors", // Cannot resolve reference
        "scala.tools.reflect.FormatInterpolator", // Reference to object without this. prefix
        "scala.tools.reflect.WrappedProperties", // Existential type
        "scala.reflect.quasiquotes.Parsers", // Reference to object without this. prefix
      ),
    ),
    CorpusProjects.ScalaCompiler.scala3 -> TextToTextTestConfig(
      minClassCount = 919,
      classExceptions = Set(
        "dotty.tools.backend.jvm.BTypesFromSymbols", // New error since Scala 3.3.1
        "dotty.tools.backend.jvm.CodeGen", // New error since Scala 3.3.1
        "dotty.tools.backend.sjs.ScopedVar", // Cannot resolve AssignmentStackElement (private class)
        "dotty.tools.dotc.ast.Trees", // Extra Nothing type argument in extends
        "dotty.tools.dotc.core.Definitions", // TODO Unknown type
        "dotty.tools.dotc.core.OrderingConstraint", // TODO Cannot resolve OrderingConstraint.ParamBounds
        "dotty.tools.dotc.parsing.Scanners", // TODO Enum cases in companion object
        "dotty.tools.dotc.quoted.PickledQuotes", // TODO Enum cases in companion object
        "dotty.tools.dotc.rewrites.Rewrites", // New error since Scala 3.3.4
        "dotty.tools.dotc.semanticdb.Scala3", // Order in enum
        "dotty.tools.dotc.transform.LambdaLift", // TODO Unknown type
        "dotty.tools.dotc.transform.sjs.JSSymUtils", // TODO Enum cases in companion object
        "dotty.tools.dotc.typer.Applications", // Extra Nothing type argument in extends, different .this qualifier
        "dotty.tools.dotc.typer.Synthesizer", // TODO Enum cases in companion object
      ),
      astLoadingFilter = false // TODO Enable
    ),

    // Scalactic
    CorpusProjects.Scalactic.scala2 -> TextToTextTestConfig(
      minClassCount = 170,
      classExceptions = Set(
        "org.scalactic.Accumulation", // No parentheses for repeated function type
        "org.scalactic.FutureSugar", // No parentheses for repeated function type
        "org.scalactic.TrySugar", // No parentheses for repeated function type
        "org.scalactic.source.TypeInfoMacro", // Cannot resolve reference
      ),
    ),
    CorpusProjects.Scalactic.scala3 -> TextToTextTestConfig(
      minClassCount = 167,
      classExceptions = Set(
        "org.scalactic.Accumulation", // No parentheses in repeated function type
        "org.scalactic.FutureSugar", // No parentheses in repeated function type
        "org.scalactic.TrySugar", // No parentheses for repeated function type
      ),
      withSources = true,
      sourceExceptions = Set(
        "org.scalactic.Every", // :\ | :\\ (in annotation)
        "org.scalactic.anyvals.NonEmptyList", // :\ | :\\ (in annotation)
      )
    ),

    // ScalaJavaTime
    CorpusProjects.ScalaJavaTime.scala2 -> TextToTextTestConfig(
      minClassCount = 186,
      classExceptions = Set(
        "java.time.temporal.TemporalAdjusters", // Private object reference
        "java.time.zone.ZoneRulesBuilder", // Private object reference
      )
    ),
    CorpusProjects.ScalaJavaTime.scala3 -> TextToTextTestConfig(
      minClassCount = 162,
      classExceptions = Set(
        "java.time.temporal.TemporalAdjusters", // Private object reference
        "java.time.zone.ZoneRulesBuilder", // Private object reference
      )
    ),

    // ScalaLibrary
    CorpusProjects.ScalaLibrary.scala2 -> TextToTextTestConfig(
      minClassCount = 787,
      classExceptions = Set(
        "scala.concurrent.impl.Promise", // Function1
      )
    ),
    CorpusProjects.ScalaLibrary.scala3 -> TextToTextTestConfig(
      minClassCount = 92,
      classExceptions = Set(
        "scala.Tuple", // _ in match types, SCL-23189
      ),
      withSources = true,
      sourceExceptions = Set(
        "scala.IArray", // cannot resolve IArray
        "scala.caps", // `*`
        "scala.annotation.MacroAnnotation", // x$1.reflect.Definition
        "scala.annotation.MainAnnotation", // duplicate annotation
        "scala.annotation.newMain", // no annotation
        "scala.quoted.Quotes", // Cannot resolve java.nio.file.Path
        "scala.quoted.runtime.QuoteMatching", // no <: _root_.scala.AnyKind
        "scala.quoted.runtime.QuoteUnpickler", // no <: _root_.scala.AnyKind
        "scala.runtime.coverage.Invoker", // no annotation
        "scala.util.TupledFunction", // duplicate annotation
      ),
    ),
    CorpusProjects.ScalaLibrary_3_8.scala3 -> TextToTextTestConfig(
      minClassCount = 910,
      classExceptions = Set(
        "scala.NamedTuple", // def map[F[_]](f: [t] => t => F[t])
        "scala.NamedTupleDecomposition", // non-absolute paths in match types
        "scala.Tuple", // non-absolute paths in match types
        "scala.caps.Contains", // with {} | with
        "scala.collection.IndexedSeqView", // extends Id | Id[_root_.scala.Nothing]
        "scala.collection.IterableOnceOps", // "\"
        "scala.collection.SeqView", // extends Appended | Appended[_root_.scala.Nothing]
        "scala.collection.SortedMapOps", // extends MapOps.LazyKeySet | MapOps.LazyKeySet[A, B, C, D]
        "scala.collection.generic.IsIterableLowPriority", // Cannot resolve _root_.scala.collection.generic.IsMap
        "scala.collection.immutable.MapOps", // extends MapOps.LazyKeySet | MapOps.LazyKeySet[A, B, C, D]
        "scala.collection.immutable.NumericRange", // extends NumericRange | NumericRange[T]
        "scala.collection.mutable.CheckedIndexedSeqView", // Id | Id[Nothing]
        "scala.collection.mutable.CollisionProofHashMap", // Cannot resolve CollisionProofHashMap.LLNode[K, V]
        "scala.concurrent.impl.Promise", // ? => ? | Function1[?, ?]
      ),
      withSources = true,
      sourceExceptions = Set(
        "scala.Array", // from: Array[A] | Unit
        "scala.AnyVal", // getClass()
        "scala.Boolean", // private (), override def getClass()
        "scala.Byte", // private (), override def getClass(), MinValue/MaxValue
        "scala.Char", // private (), override def getClass(), MinValue/MaxValue
        "scala.Double", // private (), override def getClass(), MinValue/MaxValue
        "scala.Enumeration", // SerialVersionUID(0 - 3501153230598116017L), private[ValueSet] () | private (), Enumeration.this.ValueSet | Enumeration.this.Value, implicitNotFound | implicitNotFound(ValueSet.ordMsg)
        "scala.Float", // private (), override def getClass(), MinValue/MaxValue
        "scala.Int", // private (), override def getClass(), MinValue/MaxValue
        "scala.Long", // private (), override def getClass(), MinValue/MaxValue
        "scala.Predef", // elidable | elidable(ASSERTION), @deprecated | @deprecated @deprecated
        "scala.Short", // private (), override def getClass(), MinValue/MaxValue
        "scala.StringContext", //  = ??? | N/A (macro ???)
        "scala.Unit", // private (), override def getClass()
        "scala.annotation.MacroAnnotation", // $1 | quotes
        "scala.annotation.elidable", // MinValue/MaxValue
        "scala.caps.Pure", // N/A | { this: _root_.scala.caps.Pure => }
        "scala.collection.IndexedSeqSlidingIterator", // { def hasNext: _root_.scala.Boolean = ???; def next(): C = ??? } | N/A
        "scala.collection.concurrent.INode", // Object | AnyRef
        "scala.collection.convert.StreamExtensions", // self type, private type
        "scala.collection.convert.impl.IteratorStepperBase", // final var Int | 16
        "scala.collection.generic.DefaultSerializationProxy", // { ... } | N/A
        "scala.collection.immutable.::", // private | private[::]
        "scala.collection.immutable.Node", // 31 | (1 << BitPartitionSize) - 1
        "scala.collection.immutable.Stream", // \" | "
        "scala.concurrent.ExecutionContext", // \n in annotation
        "scala.concurrent.Future", // scala.concurrent.Future.never.type | never.this.type
        "scala.concurrent.SyncChannel", // private type | N/A
        "scala.io.AnsiColor", // Escape \u001b
        "scala.io.Position", // 31 - LINE_BITS
        "scala.math.Numeric", // private type | N/A
        "scala.quoted.FromExpr", // scala.collection.immutable.Seq | scala.Seq
        "scala.quoted.runtime.QuoteMatching", // ? <: _root_.scala.AnyKind | ?
        "scala.quoted.runtime.QuoteUnpickler", // ? <: _root_.scala.AnyKind | ?
        "scala.sys.process.ProcessBuilderImpl", // ProcessBuilderImpl.this.IStreamBuilder | _root_.scala.sys.process.ProcessBuilder.IStreamBuilder
        "scala.sys.process.ProcessImpl", // ProcessImpl.this.SequentialProcess | _root_.scala.sys.process.Process.SequentialProcess
        "scala.sys.process.processInternal", // : Boolean | = props contains "scala.process.debug"
      ),
      transformed = {
        case (Content.DecompiledVsSourceOutline, s) => s
          .replaceAll(raw"@_root_\.scala\.transient\s+", "")
          .replaceAll(raw"(?<=extends )_root_\.scala\.\*:\[.+], (?=_root_\.scala\.Product)", "") // class TupleN extends _root_.scala.*:[T1, _root_.scala.EmptyTuple.type]
        case (Content.SourceOutline, s) => s
          .replaceAll(raw"@_root_\.scala\.(?:transient|annotation\.internal\.preview|annotation\.internal\.sharable)\s+", "")
          .replaceAll(raw"(?<=@_root_\.scala\.specialized)\(.+?\)", "") // specialized | specialized(Specializable.Primitives)
          .replaceAll(raw"(?<=@_root_\.scala\.throws)\[_root_\.scala\.(\w+)]\(classOf\[\1]\)", "[_root_.java.lang.$1]") // java.lang.IndexOutOfBoundsException | scala.IndexOutOfBoundsException
          .replaceAll(raw"(?<=@_root_\.scala\.throws)\[_root_\.scala\.(\w+\.)([\w.]+)]\(classOf\[\2]\)", "[_root_.java.util.$1$2]") // java.util.concurrent.TimeoutException | scala.concurrent.TimeoutException
        case (_, s) => s
      },
      aliasScala = false,
    ),

    // ScalaReflect (Scala 2 only)
    CorpusProjects.ScalaReflect.scala2 -> TextToTextTestConfig(
      minClassCount = 217,
      classExceptions = Set(
        "scala.reflect.api.TypeTags", // TypeTags.this. vs Universe.this
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
      ),
    ),

    // Scalatest
    CorpusProjects.Scalatest.scala2 -> TextToTextTestConfig(
      minClassCount = 678,
      classExceptions = Set(
        "org.scalatest.Suite", // Existential type
        "org.scalatest.enablers.InspectorAsserting", // Tuple2 type argument
        "org.scalatest.tools.Framework", // Any
        "org.scalatest.tools.Runner", // Existential type
        "org.scalatest.tools.ScalaTestAntTask", // Cannot resolve reference
        "org.scalatest.tools.ScalaTestFramework", // Any
      ),
    ),
    CorpusProjects.Scalatest.scala3 -> TextToTextTestConfig(
      minClassCount = 667,
      classExceptions = Set(
        "org.scalatest.enablers.InspectorAsserting", // Tuple2 type argument
        "org.scalatest.tools.Framework", // extends _root_.sbt.testing.Framework (in source: extends SbtFramework)
        "org.scalatest.tools.ScalaTestAntTask", // Cannot resolve org.apache.tools.ant.*
        "org.scalatest.tools.ScalaTestFramework", // Any
      ),
      withSources = true,
      sourceExceptions = Set(
        "org.scalatest.Assertions", // Multiple `extension`
        "org.scalatest.Suite", //Class[? <: AnyRef] | Class[?]
        "org.scalatest.diagrams.DiagramsMacro", // Cannot resolve x$1.reflect.Term
        "org.scalatest.events.Event", // Object vs Any
        "org.scalatest.exceptions.NotSerializableWrapperException", // case class with Serializable
        "org.scalatest.matchers.AMatcher", // with Object { toString }
        "org.scalatest.matchers.AnMatcher", // with Object { toString }
        "org.scalatest.matchers.Matcher", // T with T, T with Any, Expr[...]
        "org.scalatest.matchers.TypeMatcherMacro", // Cannot resolve x$1.reflect.Term
        "org.scalatest.matchers.dsl.EndWithWord", // with Object { toString }
        "org.scalatest.matchers.dsl.FullyMatchWord", // with Object { toString }
        "org.scalatest.matchers.dsl.IncludeWord", // with Object { toString }
        "org.scalatest.matchers.dsl.MatchPatternWord", // Expr[...]
        "org.scalatest.matchers.dsl.MatcherFactory1", // SC with SC, TC1 vs Nothing, Expr[...]
        "org.scalatest.matchers.dsl.MatcherFactory2", // SC with SC, TC1 vs Nothing, Expr[...]
        "org.scalatest.matchers.dsl.MatcherFactory3", // SC with SC, TC1 vs Nothing, Expr[...]
        "org.scalatest.matchers.dsl.MatcherFactory4", // SC with SC, TC1 vs Nothing, Expr[...]
        "org.scalatest.matchers.dsl.MatcherFactory5", // SC with SC, TC1 vs Nothing, Expr[...]
        "org.scalatest.matchers.dsl.MatcherFactory6", // SC with SC, TC1 vs Nothing, Expr[...]
        "org.scalatest.matchers.dsl.MatcherFactory7", // SC with SC, TC1 vs Nothing, Expr[...]
        "org.scalatest.matchers.dsl.MatcherFactory8", // SC with SC, TC1 vs Nothing, Expr[...]
        "org.scalatest.matchers.dsl.NotWord", // Expr[...]
        "org.scalatest.matchers.dsl.ResultOfNotWordForAny", // Expr[...]
        "org.scalatest.matchers.dsl.StartWithWord", // with Object { toString }
        "org.scalatest.matchers.must.Matchers", // Multiple `extension`
        "org.scalatest.matchers.must.TypeMatcherMacro", // Cannot resolve x$1.reflect.Term
        "org.scalatest.matchers.should.Matchers", // Multiple `extension`
        "org.scalatest.matchers.should.TypeMatcherMacro", // Cannot resolve x$1.reflect.Term
        "org.scalatest.tools.Runner", // Class[? <: AnyRef] | Class[?] (private[scalatest], FromJavaObject)
        "org.scalatest.tools.StringReporter", // Unicode \u001b char
        "org.scalatest.wordspec.AsyncWordSpecLike", // Expr[...]
      )
    ),

    // Scalaz
    CorpusProjects.Scalaz.scala2 -> TextToTextTestConfig(
      minClassCount = 1588,
      classExceptions = Set(
        "scalaz.Foralls", // Excessive parentheses in existential type
        "scalaz.FreeFunctions", // Tuple2
        "scalaz.LanApply", // Any
        "scalaz.std.StringInstances", // No this. prefix for object
        "scalaz.syntax.ToApplicativeErrorOps", // Existential type
        "scalaz.syntax.ToMonadErrorOps", // Existential type
        "scalaz.syntax.ToMonadTellOps", // Existential type
      )
    ),
    CorpusProjects.Scalaz.scala3 -> TextToTextTestConfig(
      minClassCount = 1588,
      classExceptions = Set(
        "scalaz.\\&/", // id$
        "scalaz.\\/", // id$
      )
    ),

    // ZIO
    CorpusProjects.Zio.scala2 -> TextToTextTestConfig(
      minClassCount = 266,
    ),
    CorpusProjects.Zio.scala3 -> TextToTextTestConfig(
      minClassCount = 266,
      classExceptions = Set(
        "zio.Experimental", // _root_.scala.runtime.$throws[A, E] | Failure(Cannot resolve reference $throws)
        "zio.internal.stacktracer.SourceLocation", // Given without a name
      ),
      withSources = true,
      classesWithoutSource = Set(
        "zio.BuildInfo",
        "zio.internal.stacktracer.BuildInfo",
        "zio.stream.BuildInfo",
      ),
      sourceExceptions = Set(
        "zio.Cause", // private | private[Cause]
        "zio.Experimental", // scala.runtime.$throws[A, E] | scala.throws[A, E]
        "zio.Fiber", // case class extends Product & Serializable
        "zio.FiberRef", // private | private[FiberRef]
        "zio.FiberRefs", // @scala.specialized | @scala.specialized(SpecializeInt)
        "zio.HasNoScope", // \n | ' ' (in annotation)
        "zio.HasNoScopeCompanionVersionSpecific", // transparent inline given | final transparent inline given
        "zio.RuntimeFlag", // reference constants
        "zio.Scope", // private type alias
        "zio.ZEnvironment", // izumi.reflect.macrortti
        "zio.ZLayer", // private[Derive]
        "zio.internal.FiberRuntime", // x * y constant
        "zio.internal.LinkedQueue", // Int.MaxValue constant
        "zio.internal.PartitionedRingBuffer", // nQueues * partitionSize constant
        "zio.internal.WeakConcurrentBag", // zio.Duration | DurationModule.this.Duration
        "zio.internal.WeakConcurrentBagGc", // zio.Duration | DurationModule.this.Duration
        "zio.internal.macros.ZLayerDerivationMacros", // Expr[...]
        "zio.internal.macros.LayerMacros", // Expr[...]
        "zio.metrics.MetricPair", // private type alias
        "zio.stm.STM", // zio.BuildFrom vs BuildFromCompat.this.BuildFrom
        "zio.stream.ZChannel", // zio.EnvironmentTag vs VersionSpecific.this.EnvironmentTag
      )
    ),
  ).withDefaultValue(default)
}
