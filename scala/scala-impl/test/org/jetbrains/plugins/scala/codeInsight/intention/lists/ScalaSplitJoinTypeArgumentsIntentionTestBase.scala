package org.jetbrains.plugins.scala.codeInsight.intention.lists

abstract class ScalaSplitJoinTypeArgumentsIntentionTestBase extends ScalaSplitJoinLineIntentionTestBase {
  private def doTest(singleLineText: String, multiLineText: String): Unit =
    doTest(singleLineText, multiLineText, listStartChar = '[')

  // Method Calls

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

  def testMethodCallWithOneArg(): Unit =
    checkIntentionIsNotAvailable(
      """def foo[A]: Unit = {}
        |
        |object Test {
        |  foo[Int]
        |}""".stripMargin
    )

  def testMethodCallWithOneArgTrailingComma(): Unit =
    checkIntentionIsNotAvailable(
      """def foo[A]: Unit = {}
        |
        |object Test {
        |  foo[Int,]
        |}""".stripMargin
    )

  // Class creation

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

  def testClassCreationWithOneArg(): Unit =
    checkIntentionIsNotAvailable(
      """class Foo[A]
        |
        |object Test {
        |  new Foo[Int]
        |}""".stripMargin
    )

  def testClassCreationWithOneArgTrailingComma(): Unit =
    checkIntentionIsNotAvailable(
      """class Foo[A]
        |
        |object Test {
        |  new Foo[Int,]
        |}""".stripMargin
    )

  // Case class creation

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

  def testCaseClassCreationWithOneArg(): Unit =
    checkIntentionIsNotAvailable(
      """case class Foo[A]()
        |
        |object Test {
        |  Foo[Int]()
        |}""".stripMargin
    )

  def testCaseClassCreationWithOneArgTrailingComma(): Unit =
    checkIntentionIsNotAvailable(
      """case class Foo[A]()
        |
        |object Test {
        |  Foo[Int,]()
        |}""".stripMargin
    )
}
