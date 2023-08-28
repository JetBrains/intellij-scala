package org.jetbrains.plugins.scala.text.scala3

import org.jetbrains.plugins.scala.DependencyManagerBase.RichStr
import org.jetbrains.plugins.scala.text.TextToTextTestBase

class QuillTest extends TextToTextTestBase(
  Seq(
    "io.getquill" %% "quill-sql" % "4.6.0",
    "io.getquill" %% "quill-jdbc-zio" % "4.6.0"
  ),
  Seq("io.getquill"), Set.empty , 658,
  Set(
    "io.getquill.DynamicInsert", // No parentheses for repeated function type
    "io.getquill.EntityQueryModel", // No parentheses for repeated function type
    "io.getquill.InfixInterpolator", // Top-level definitions
    "io.getquill.InjectableEagerPlanter", // Function1
    "io.getquill.Insert", // No parentheses for repeated function type
    "io.getquill.MirrorColumnResolving", // Given
    "io.getquill.MirrorContextBase", // Inline parameter
    "io.getquill.OuterSelectWrap", // Given
    "io.getquill.SqlInfixInterpolator", // Top-level definitions
    "io.getquill.ToDynamicAction", // Top-level definitions
    "io.getquill.ToDynamicActionReturning", // Top-level definitions
    "io.getquill.ToDynamicEntityQuery", // Top-level definitions
    "io.getquill.ToDynamicInsert", // Top-level definitions
    "io.getquill.ToDynamicQuery", // Top-level definitions
    "io.getquill.ToDynamicUpdate", // Top-level definitions
    "io.getquill.ast.EqualityOperator", // No case object _==
    "io.getquill.dsl.MetaDsl", // No parentheses for repeated function type
    "io.getquill.dsl.QueryDsl", // No parentheses for repeated function type
    "io.getquill.context.Context", // Inline parameter
    "io.getquill.context.ContextTranslateMacro", // Inline parameter
    "io.getquill.context.ContextVerbPrepare", // Inline parameter
    "io.getquill.context.ContextVerbStream", // Inline parameter
    "io.getquill.context.DatasourceContextInjectionMacro", // Inline parameter
    "io.getquill.context.Execution", // Given
    "io.getquill.context.InsertUpdateMacro", // Given
    "io.getquill.context.LiftMacro", // Function1
    "io.getquill.context.Particularize", // Given
    "io.getquill.context.QueryExecution", // Inline parameter
    "io.getquill.context.QueryExecutionBatch", // Inline parameter
    "io.getquill.context.QueryExecutionBatchModel", // Given
    "io.getquill.context.QuerySingleAsQuery", // Inline parameter
    "io.getquill.context.jdbc.Decoders", // Decoders.this vs JdbcContextTypes.this
    "io.getquill.context.jdbc.Encoders", // Encoders.this vs JdbcContextTypes.this
    "io.getquill.context.jdbc.JdbcContext", // Inline parameter
    "io.getquill.context.qzio.ZioJdbcContext", // Inline parameter
    "io.getquill.context.qzio.ZioJdbcUnderlyingContext", // Inline parameter
    "io.getquill.dsl.InfixDsl", // No annotations
    "io.getquill.generic.TupleMember", // Inline parameter
    "io.getquill.jdbczio.QuillBaseContext", // Inline parameter
    "io.getquill.metaprog.InjectableEagerPlanterExpr", // Function1
    "io.getquill.metaprog.TranspileConfigLiftable", // Given
    "io.getquill.metaprog.etc.ColumnsFlicer", // Inline parameter
    "io.getquill.metaprog.etc.ListFlicer", // Inline parameter
    "io.getquill.metaprog.etc.MapFlicer", // Inline parameter
    "io.getquill.norm.SheathLeafClauses", // Cannot resolve reference
    "io.getquill.parser.AstPicklers", // Given
    "io.getquill.parser.Lifter", // Given
    "io.getquill.parser.OperationsParser", // TODO > symbol
    "io.getquill.parser.ParserHelpers", // TODO using(x: Int, Long)
    "io.getquill.parser.Unlifter", // Given
    "io.getquill.quat.QuatMakingBase", // $1n in extension, SCL-21551
    "io.getquill.util.debug.PrintMac", // Inline parameter
  )
)