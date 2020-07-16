package org.jetbrains.plugins.scala.lang.autoImport

import org.jetbrains.plugins.scala.autoImport.quickFix.ScalaImportGlobalMemberFix
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScReferenceExpression

class ImportStableWithPrefixFixTest extends ImportElementFixTestBase[ScReferenceExpression] {
  override def createFix(element: ScReferenceExpression) =
    ScalaImportGlobalMemberFix.fixWithPrefix(element)

  def testNextInt(): Unit = doTest(
    fileText =
      s"""
         |object Test {
         |  ${CARET}nextInt()
         |}
         |""".stripMargin,
    expectedText =
      """
        |import scala.util.Random
        |
        |object Test {
        |  Random.nextInt()
        |}
        |""".stripMargin,

    "scala.util.Random.nextInt"
  )

  def testEmptyList(): Unit = doTest(
    fileText =
      s"""
         |object Test {
         |  ${CARET}emptyList()
         |}
         |""".stripMargin,
    expectedText =
      """
        |import java.util.Collections
        |
        |object Test {
        |  Collections.emptyList()
        |}
        |""".stripMargin,

    "java.util.Collections.emptyList",
  )

  def testConstantAsPattern(): Unit = doTest(
    fileText =
      s"""
         |class Test {
         |  0.0 match {
         |    case ${CARET}PositiveInfinity =>
         |  }
         |}
         |""".stripMargin,
    expectedText =
      """
        |class Test {
        |  0.0 match {
        |    case Double.PositiveInfinity =>
        |  }
        |}
        |""".stripMargin,

    selected = "scala.Double.PositiveInfinity"
  )

  def testManyCandidates(): Unit = doTest(
    fileText =
      s"""
         |class Test {
         |  ${CARET}empty
         |}
         |""".stripMargin,

    expectedText =
      s"""
         |import scala.collection.mutable
         |
         |class Test {
         |  mutable.Set.empty
         |}
         |""".stripMargin,

    "scala.collection.mutable.Set.empty"
  )

  def testCompanionObjectValue(): Unit = doTest(
    fileText =
      s"""
         |trait Foo {
         |  ${CARET}foo
         |}
         |
         |object Foo {
         |  val (_, foo) = ???
         |}""".stripMargin,
    expectedText =
      s"""
         |trait Foo {
         |  Foo.foo
         |}
         |
         |object Foo {
         |  val (_, foo) = ???
         |}""".stripMargin,

    selected = "Foo.foo"
  )

  def testCompanionObjectMethod(): Unit = doTest(
    fileText =
      s"""
         |class Foo {
         |  ${CARET}foo
         |}
         |
         |object Foo {
         |  def foo(): Unit = {}
         |}
         |""".stripMargin,
    expectedText =
      """
        |class Foo {
        |  Foo.foo
        |}
        |
        |object Foo {
        |  def foo(): Unit = {}
        |}""".stripMargin,
    selected = "Foo.foo"
  )
}
