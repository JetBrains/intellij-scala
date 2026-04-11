package org.jetbrains.plugins.scala.codeInsight.intention.lists

import org.junit.Test

abstract class ScalaSplitJoinTypeArgumentsIntentionTestBase extends ScalaSplitJoinLineIntentionTestBase {
  private def doTest(singleLineText: String, multiLineText: String): Unit =
    doTest(singleLineText, multiLineText, listStartChar = '[')

  // Method Calls

  @Test
  def testMethodCall(): Unit =
    doTest(
      singleLineText =
        """def foo[A, B]: Unit = {}
          |
          |object Test {
          |  foo[Int, String]
          |}""".stripMargin,
      multiLineText =
        """def foo[A, B]: Unit = {}
          |
          |object Test {
          |  foo[
          |    Int,
          |    String
          |  ]
          |}""".stripMargin
    )

  @Test
  def testMethodCallTrailingComma(): Unit =
    doTest(
      singleLineText =
        """def foo[A, B]: Unit = {}
          |
          |object Test {
          |  foo[Int, String, ]
          |}""".stripMargin,
      multiLineText =
        """def foo[A, B]: Unit = {}
          |
          |object Test {
          |  foo[
          |    Int,
          |    String,
          |  ]
          |}""".stripMargin
    )

  @Test
  def testMethodCallNamedTypeArgs(): Unit =
    doTest(
      singleLineText =
        """import scala.language.experimental.namedTypeArguments
          |
          |def foo[A, B]: Unit = {}
          |
          |object Test {
          |  foo[A = Int, B = String]
          |}""".stripMargin,
      multiLineText =
        """import scala.language.experimental.namedTypeArguments
          |
          |def foo[A, B]: Unit = {}
          |
          |object Test {
          |  foo[
          |    A = Int,
          |    B = String
          |  ]
          |}""".stripMargin
    )

  @Test
  def testMethodCallWithOneArg(): Unit =
    checkIntentionIsNotAvailable(
      """def foo[A]: Unit = {}
        |
        |object Test {
        |  foo[Int]
        |}""".stripMargin
    )

  @Test
  def testMethodCallWithOneArgTrailingComma(): Unit =
    checkIntentionIsNotAvailable(
      """def foo[A]: Unit = {}
        |
        |object Test {
        |  foo[Int,]
        |}""".stripMargin
    )

  // Class creation

  @Test
  def testClassCreation(): Unit =
    doTest(
      singleLineText =
        """class Foo[A, B]
          |
          |object Test {
          |  new Foo[Int, String]
          |}""".stripMargin,
      multiLineText =
        """class Foo[A, B]
          |
          |object Test {
          |  new Foo[
          |    Int,
          |    String
          |  ]
          |}""".stripMargin
    )

  @Test
  def testClassCreationTrailingComma(): Unit =
    doTest(
      singleLineText =
        """class Foo[A, B]
          |
          |object Test {
          |  new Foo[Int, String, ]
          |}""".stripMargin,
      multiLineText =
        """class Foo[A, B]
          |
          |object Test {
          |  new Foo[
          |    Int,
          |    String,
          |  ]
          |}""".stripMargin
    )

  @Test
  def testClassCreationWithOneArg(): Unit =
    checkIntentionIsNotAvailable(
      """class Foo[A]
        |
        |object Test {
        |  new Foo[Int]
        |}""".stripMargin
    )

  @Test
  def testClassCreationWithOneArgTrailingComma(): Unit =
    checkIntentionIsNotAvailable(
      """class Foo[A]
        |
        |object Test {
        |  new Foo[Int,]
        |}""".stripMargin
    )

  // Case class creation

  @Test
  def testCaseClassCreation(): Unit =
    doTest(
      singleLineText =
        """case class Foo[A, B]()
          |
          |object Test {
          |  Foo[Int, String]()
          |}""".stripMargin,
      multiLineText =
        """case class Foo[A, B]()
          |
          |object Test {
          |  Foo[
          |    Int,
          |    String
          |  ]()
          |}""".stripMargin
    )

  @Test
  def testCaseClassCreationTrailingComma(): Unit =
    doTest(
      singleLineText =
        """case class Foo[A, B]()
          |
          |object Test {
          |  Foo[Int, String, ]()
          |}""".stripMargin,
      multiLineText =
        """case class Foo[A, B]()
          |
          |object Test {
          |  Foo[
          |    Int,
          |    String,
          |  ]()
          |}""".stripMargin
    )

  @Test
  def testCaseClassCreationWithOneArg(): Unit =
    checkIntentionIsNotAvailable(
      """case class Foo[A]()
        |
        |object Test {
        |  Foo[Int]()
        |}""".stripMargin
    )

  @Test
  def testCaseClassCreationWithOneArgTrailingComma(): Unit =
    checkIntentionIsNotAvailable(
      """case class Foo[A]()
        |
        |object Test {
        |  Foo[Int,]()
        |}""".stripMargin
    )
}
