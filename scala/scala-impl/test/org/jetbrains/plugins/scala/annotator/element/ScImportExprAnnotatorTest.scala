package org.jetbrains.plugins.scala.annotator.element

import org.jetbrains.plugins.scala.annotator.Message.Error
import org.jetbrains.plugins.scala.annotator.ScalaHighlightingTestBase
import org.jetbrains.plugins.scala.{LatestScalaVersions, ScalaBundle, ScalaVersion}

abstract class ScImportExprAnnotatorTestBase extends ScalaHighlightingTestBase {
  protected val ImportShouldBeQualifiedMessage = ScalaBundle.message("import.expr.should.be.qualified")

  def testUnqualifiedImport(): Unit =
    assertErrors(
      """object Foo
        |
        |object Bar {
        |  import Foo
        |}
        |""".stripMargin,
      Error("Foo", ImportShouldBeQualifiedMessage)
    )

  def testUnqualifiedRenamingImport(): Unit = {
    assertErrors(
      """object Foo
        |
        |object Bar {
        |  import Foo => FooRenamed
        |}
        |""".stripMargin,
      Error("Foo", ImportShouldBeQualifiedMessage),
      Error("FooRenamed", ScalaBundle.message("cannot.resolve", "FooRenamed"))
    )
  }

  def testQualifiedImport(): Unit =
    assertNoErrors(
      """object Foo {
        |  object Inner
        |}
        |
        |object Bar {
        |  import Foo.Inner
        |}
        |""".stripMargin
    )

  def testQualifiedImportWithMultipleSelectors(): Unit =
    assertNoErrors(
      """object Foo {
        |  object Inner1
        |  object Inner2
        |}
        |
        |object Bar {
        |  import Foo.{Inner1, Inner2}
        |}
        |""".stripMargin
    )

  def testQualifiedRenamingImport(): Unit =
    assertNoErrors(
      """object Foo {
        |  object Inner
        |}
        |
        |object Bar {
        |  import Foo.{Inner => InnerRenamed}
        |}
        |""".stripMargin
    )
}

final class ScImportExprAnnotatorTest extends ScImportExprAnnotatorTestBase {
  override protected def supportedIn(version: ScalaVersion): Boolean = version == LatestScalaVersions.Scala_2_13
}

final class ScImportExprAnnotatorTest_Scala3 extends ScImportExprAnnotatorTestBase {
  override protected def supportedIn(version: ScalaVersion): Boolean = version == LatestScalaVersions.Scala_3

  def testUnqualifiedRenamingImportSc3(): Unit = {
    assertNoErrors(
      """object Foo
        |
        |object Bar {
        |  import Foo as FooRenamed
        |}
        |""".stripMargin
    )
  }

  def testUnqualifiedRenamingImportSc3_wildcard(): Unit = {
    assertNoErrors(
      """object Foo
        |
        |object Bar {
        |  import Foo as _
        |}
        |""".stripMargin
    )
  }

  def testQualifiedRenamingImportSc3(): Unit =
    assertNoErrors(
      """object Foo {
        |  object Inner
        |}
        |
        |object Bar {
        |  import Foo.{Inner as InnerRenamed}
        |}
        |""".stripMargin
    )

  def testQualifiedRenamingImportSc3_braceless(): Unit =
    assertNoErrors(
      """object Foo {
        |  object Inner
        |}
        |
        |object Bar {
        |  import Foo.Inner as InnerRenamed
        |}
        |""".stripMargin
    )
}
