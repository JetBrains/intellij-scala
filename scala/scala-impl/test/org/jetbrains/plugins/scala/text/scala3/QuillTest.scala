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
    "io.getquill.context.InsertUpdateMacro", // Enum
    "io.getquill.context.LiftMacro", // Function1
    "io.getquill.context.jdbc.Decoders", // Decoders.this vs JdbcContextTypes.this
    "io.getquill.context.jdbc.Encoders", // Encoders.this vs JdbcContextTypes.this
    "io.getquill.context.qzio.ZioJdbcContext", // Extra default arguments
    "io.getquill.context.qzio.ZioJdbcUnderlyingContext", // Extra default arguments
    "io.getquill.dsl.InfixDsl", // No annotations
    "io.getquill.metaprog.InjectableEagerPlanterExpr", // Function1
    "io.getquill.norm.SheathLeafClauses", // Cannot resolve reference
    "io.getquill.parser.OperationsParser", // TODO > symbol
    "io.getquill.parser.ParserHelpers", // TODO using(x: Int, Long)
    "io.getquill.quat.QuatMakingBase", // $1n in extension, SCL-21551
  )
)