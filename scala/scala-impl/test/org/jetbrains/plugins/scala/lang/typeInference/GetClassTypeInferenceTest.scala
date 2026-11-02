package org.jetbrains.plugins.scala.lang.typeInference

import org.jetbrains.plugins.scala.ScalaVersion

abstract class GetClassTypeInferenceTestBase extends TypeInferenceTestBase {
  private def wildcard: String = if (version.isScala3) "?" else "_"

  def testStringLiteral(): Unit = doTest(
    s"""${START}"".getClass$END
       |//Class[$wildcard <: String]
       |""".stripMargin
  )

  def testStringLiteralWithParentheses(): Unit = doTest(
    s"""${START}"".getClass()$END
       |//Class[$wildcard <: String]
       |""".stripMargin
  )

  def testLiteralSingleton(): Unit = doTest(
    s"""val s: "text" = "text"
       |${START}s.getClass$END
       |//Class[$wildcard <: String]
       |""".stripMargin
  )

  def testSingletonReference(): Unit = doTest(
    s"""val s = "text"
       |val t: s.type = s
       |${START}t.getClass$END
       |//Class[$wildcard <: String]
       |""".stripMargin
  )

  def testGenericThis(): Unit = doTest(
    s"""class C[A] {
       |  ${START}this.getClass$END
       |}
       |//Class[$wildcard <: C[A]]
       |""".stripMargin
  )

  def testImplicitGenericThis(): Unit = doTest(
    s"""class C[A] {
       |  ${START}getClass$END
       |}
       |//Class[$wildcard <: C[A]]
       |""".stripMargin
  )
}

class GetClassTypeInferenceTest_Scala2 extends GetClassTypeInferenceTestBase {
  override protected def supportedIn(version: ScalaVersion): Boolean = version.isScala2
}

class GetClassTypeInferenceTest_Scala3 extends GetClassTypeInferenceTestBase {
  override protected def supportedIn(version: ScalaVersion): Boolean = version.isScala3
}
