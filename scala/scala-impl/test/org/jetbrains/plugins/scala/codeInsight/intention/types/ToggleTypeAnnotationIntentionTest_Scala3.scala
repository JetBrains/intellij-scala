package org.jetbrains.plugins.scala.codeInsight.intention.types

import org.jetbrains.plugins.scala.{LatestScalaVersions, ScalaVersion}

final class ToggleTypeAnnotationIntentionTest_Scala3 extends ToggleTypeAnnotationIntentionTestBase {
  override protected def supportedIn(version: ScalaVersion): Boolean = version >= LatestScalaVersions.Scala_3_0

  override def testAddTypeToMatchPattern(): Unit = doTest(
    s"""
       |object Test {
       |  0 match {
       |    case x$caretTag =>
       |  }
       |}
       |""".stripMargin,
    s"""
       |object Test {
       |  0 match {
       |    case x$caretTag: 0 =>
       |  }
       |}
       |""".stripMargin
  )

  override def testAddTypeAnnotationWithTypeWildCard(): Unit = doTest(
    s"""
       |class Foo[T]
       |
       |abstract class A {
       |  def b(): Foo[?]
       |}
       |
       |class B extends A {
       |  protected def b$caretTag() = new Foo[?]
       |}
       |""".stripMargin,
    s"""
       |class Foo[T]
       |
       |abstract class A {
       |  def b(): Foo[?]
       |}
       |
       |class B extends A {
       |  protected def b$caretTag(): Foo[?] = new Foo[?]
       |}
       |""".stripMargin
  )
}
