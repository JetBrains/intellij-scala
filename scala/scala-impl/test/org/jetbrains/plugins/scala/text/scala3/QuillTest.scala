package org.jetbrains.plugins.scala.text.scala3

import org.jetbrains.plugins.scala.DependencyManagerBase.RichStr
import org.jetbrains.plugins.scala.text.TextToTextTestBase

class QuillTest extends TextToTextTestBase(
  Seq(
    "io.getquill" %% "quill-sql" % "4.6.0",
    "io.getquill" %% "quill-jdbc-zio" % "4.6.0"
  ),
  Seq("io.getquill"), Set("io.getquill.parser") /* ContextFunction */ , 619,
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
    "io.getquill.extras", // Extension
    "io.getquill.ast.EqualityOperator", // No case object _==
    "io.getquill.dsl.MetaDsl", // No parentheses for repeated function type
    "io.getquill.dsl.QueryDsl", // No parentheses for repeated function type
    "io.getquill.context.BatchStatic", // Extension
    "io.getquill.context.Context", // Extension
    "io.getquill.context.ContextTranslateMacro", // Inline parameter
    "io.getquill.context.ContextVerbPrepare", // Inline parameter
    "io.getquill.context.ContextVerbStream", // Inline parameter
    "io.getquill.context.DatasourceContextInjectionMacro", // Inline parameter
    "io.getquill.context.Execution", // Given
    "io.getquill.context.InsertUpdateMacro", // Given
    "io.getquill.context.LiftMacro", // Function1
    "io.getquill.context.Particularize", // Extension, given
    "io.getquill.context.QueryExecution", // Inline parameter
    "io.getquill.context.QueryExecutionBatch", // Inline parameter
    "io.getquill.context.QueryExecutionBatchDynamic", // Extension
    "io.getquill.context.QueryExecutionBatchModel", // Given
    "io.getquill.context.QuerySingleAsQuery", // Inline parameter
    "io.getquill.context.ReflectivePathChainLookup", // Inline parameter
    "io.getquill.context.StaticSpliceMacro", // Extension
    "io.getquill.context.jdbc.Decoders", // Decoders.this vs JdbcContextTypes.this
    "io.getquill.context.jdbc.Encoders", // Encoders.this vs JdbcContextTypes.this
    "io.getquill.context.jdbc.JdbcContext", // Inline parameter
    "io.getquill.context.qzio.ZioJdbcContext", // Inline parameter
    "io.getquill.context.qzio.ZioJdbcUnderlyingContext", // Inline parameter
    "io.getquill.dsl.InfixDsl", // No annotations
    "io.getquill.generic.ElaborateStructure", // No AnyKind upper type bound, extension
    "io.getquill.generic.TupleMember", // Inline parameter
    "io.getquill.jdbczio.QuillBaseContext", // Inline parameter
    "io.getquill.metaprog.Extractors", // Extension
    "io.getquill.metaprog.InjectableEagerPlanterExpr", // Function1
    "io.getquill.metaprog.TranspileConfigLiftable", // Extension, given, ContextFunction
    "io.getquill.metaprog.TypeExtensions", // Extension
    "io.getquill.metaprog.etc.ColumnsFlicer", // Inline parameter
    "io.getquill.metaprog.etc.ListFlicer", // Inline parameter
    "io.getquill.metaprog.etc.MapFlicer", // Inline parameter
    "io.getquill.norm.SheathLeafClauses", // Cannot resolve reference
    "io.getquill.quat.QuatMakingBase", // Extension
    "io.getquill.util.CommonExtensions", // Extension
    "io.getquill.util.debug.PrintMac", // Inline parameter
  )
)