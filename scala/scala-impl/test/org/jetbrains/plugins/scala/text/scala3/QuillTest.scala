package org.jetbrains.plugins.scala.text.scala3

import org.jetbrains.plugins.scala.DependencyManagerBase.RichStr
import org.jetbrains.plugins.scala.text.TextToTextTestBase

class QuillTest extends TextToTextTestBase(
  Seq(
    "io.getquill" %% "quill-sql" % "4.6.0",
    "io.getquill" %% "quill-jdbc-zio" % "4.6.0"
  ),
  Seq("io.getquill"), Set("io.getquill.metaprog", "io.getquill.parser") /* Reflect, ContextFunction */ , 597,
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
    "io.getquill.context.BatchStatic", // Extension, non-canonical | type
    "io.getquill.context.Context", // Extension
    "io.getquill.context.ContextTranslateMacro", // Inline parameter
    "io.getquill.context.ContextVerbPrepare", // Inline parameter
    "io.getquill.context.ContextVerbStream", // Inline parameter
    "io.getquill.context.DatasourceContextInjectionMacro", // Inline parameter
    "io.getquill.context.Execution", // Given
    "io.getquill.context.InsertUpdateMacro", // Given, non-canonical | type
    "io.getquill.context.LiftMacro", // Function1
    "io.getquill.context.Particularize", // Extension, given
    "io.getquill.context.QueryExecution", // Inline parameter
    "io.getquill.context.QueryExecutionBatch", // Inline parameter, non-canonical & type
    "io.getquill.context.QueryExecutionBatchDynamic", // Extension, non-canonical & type
    "io.getquill.context.QueryExecutionBatchModel", // Given, non-canonical | type
    "io.getquill.context.QuerySingleAsQuery", // Inline parameter
    "io.getquill.context.ReflectivePathChainLookup", // Inline parameter
    "io.getquill.context.StaticSpliceMacro", // Cannot resolve reference
    "io.getquill.context.jdbc.Decoders", // Decoders.this vs JdbcContextTypes.this
    "io.getquill.context.jdbc.Encoders", // Encoders.this vs JdbcContextTypes.this
    "io.getquill.context.jdbc.JdbcContext", // Inline parameter
    "io.getquill.context.qzio.ZioJdbcContext", // Inline parameter
    "io.getquill.context.qzio.ZioJdbcUnderlyingContext", // Inline parameter
    "io.getquill.dsl.InfixDsl", // No annotations
    "io.getquill.generic.ConstructDecoded", // No AnyKind upper type bound
    "io.getquill.generic.ConstructType", // No AnyKind upper type bound
    "io.getquill.generic.DeconstructElaboratedEntityLevels", // No AnyKind upper type bound
    "io.getquill.generic.ElaborateStructure", // No AnyKind upper type bound, extension
    "io.getquill.generic.GenericDecoder", // No AnyKind upper type bound
    "io.getquill.generic.TupleMember", // Inline parameter
    "io.getquill.jdbczio.QuillBaseContext", // Inline parameter
    "io.getquill.norm.SheathLeafClauses", // Cannot resolve reference
    "io.getquill.quat.QuatMaking", // Cannot resolve reference
    "io.getquill.quat.QuatMakingBase", // Cannot resolve reference
    "io.getquill.util.CommonExtensions", // Extension
    "io.getquill.util.Format", // No AnyKind upper type bound
    "io.getquill.util.Load", // Cannot resolve reference
    "io.getquill.util.debug.PrintMac", // Inline parameter
  )
)