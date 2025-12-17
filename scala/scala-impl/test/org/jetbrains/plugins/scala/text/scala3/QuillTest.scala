package org.jetbrains.plugins.scala.text.scala3

import org.jetbrains.plugins.scala.DependencyManagerBase.RichStr
import org.jetbrains.plugins.scala.text.TextToTextTestBase
import org.jetbrains.plugins.scala.text.TextToTextTestBase.Content.{DecompiledVsSourceOutline, SourceOutline}

class QuillTest extends TextToTextTestBase(
  dependencies = Seq(
    "io.getquill" %% "quill-sql" % "4.8.4",
    "io.getquill" %% "quill-jdbc-zio" % "4.8.4"
  ),
  packages = Seq("io.getquill"),
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
    "io.getquill.StringCodec", // ... | Cannot resolve expression
    "io.getquill.ast.StatefulTransformerWithStack", // scala.Option | scala.None.type
    "io.getquill.context.BatchStatic", // io.getquill.ast.Ident | scala.Any
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
    "io.getquill.context.sql.idiom.SqlIdiom", // SqlIdiom | SqlIdiom with _root_.java.lang.Object { def...
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
    case (DecompiledVsSourceOutline, s) =>
      s.replaceAll("@_root_\\.scala\\.annotation\\.compileTimeOnly.*\\s+", "") // Reference as annotation argument
        .replaceAll("x\\$\\d: _root_.scala.quoted.Quotes", "_root_.scala.quoted.Quotes") // SCL-21552
        .replaceAll("x\\$\\d.reflect", "_root_.scala.quoted.quotes.reflect") // SCL-21552
        .replace(" <: _root_.scala.AnyKind", "") // SCL-21240
    case (SourceOutline, s) =>
      s.replaceAll("@_root_\\.scala\\.annotation\\.compileTimeOnly.*\\s+", "") // Reference as annotation argument
    case (_, s) => s
  }
)