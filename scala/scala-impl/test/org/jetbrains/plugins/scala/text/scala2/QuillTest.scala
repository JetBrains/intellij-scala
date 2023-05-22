package org.jetbrains.plugins.scala.text.scala2

import org.jetbrains.plugins.scala.DependencyManagerBase.RichStr
import org.jetbrains.plugins.scala.text.TextToTextTestBase

class QuillTest extends TextToTextTestBase(
  Seq(
    "io.getquill" %% "quill-sql" % "4.6.0",
    "io.getquill" %% "quill-jdbc-zio" % "4.6.0"
  ),
  Seq("io.getquill"), Set.empty, 505,
  Set(
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
  includeScalaReflect = true
)