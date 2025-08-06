package org.jetbrains.plugins.scala.lang.completion3

import org.jetbrains.plugins.scala.ScalaVersion
import org.jetbrains.plugins.scala.lang.completion3.base.ScalaCompletionTestBase

import java.nio.file.Path

class Scala3GlobalMemberCompletionTest extends ScalaCompletionTestBase {
  override protected def supportedIn(version: ScalaVersion) = version >= ScalaVersion.Latest.Scala_3_0

  override def getTestDataPath: String =
    s"${super.getTestDataPath}globalMember3"

  override protected def sourceRootPath: Path = Path.of(getTestDataPath)

  def testExtensionMethod(): Unit = doCompletionTest(
    fileText =
      s""""foobar".fiThC$CARET
         |""".stripMargin,
    resultText =
      """import tests.Extensions1.firstThreeChars
        |
        |"foobar".firstThreeChars
        |""".stripMargin,
    item = "firstThreeChars",
    invocationCount = 2
  )

  def testNoCompletionForPrivateExtensionMethod(): Unit = checkNoCompletion(
    fileText =
      s"""2.imposToR$CARET
         |""".stripMargin,
    invocationCount = 2
  )()

  def testNoCompletionForLocalExtensionMethod(): Unit = checkNoCompletion(
    fileText =
      s"""false.unreaLocEx$CARET
         |""".stripMargin,
    invocationCount = 2
  )()

  def testExtensionMethod2(): Unit = doCompletionTest(
    fileText =
      s"""import tests.Foo
         |
         |object Test {
         |  val foo = Foo('z')
         |  foo.toC$CARET
         |}
         |
         |package tests:
         |  final case class Foo(ch: Char)
         |
         |  object Extensions4:
         |    extension (foo: Foo)
         |      def toChar: Char = foo.ch
         |end tests
         |""".stripMargin,
    resultText =
      """import tests.Extensions4.toChar
        |import tests.Foo
        |
        |object Test {
        |  val foo = Foo('z')
        |  foo.toChar
        |}
        |
        |package tests:
        |  final case class Foo(ch: Char)
        |
        |  object Extensions4:
        |    extension (foo: Foo)
        |      def toChar: Char = foo.ch
        |end tests
        |""".stripMargin,
    item = "toChar",
    invocationCount = 2
  )

  def testTopLevelExtensionMethod(): Unit = doCompletionTest(
    fileText =
      s"""import tests.Foo
         |
         |object Test {
         |  val foo = Foo('z')
         |  foo.toC$CARET
         |}
         |
         |package tests:
         |  final case class Foo(ch: Char)
         |
         |  extension (foo: Foo)
         |    def toChar: Char = foo.ch
         |end tests
         |""".stripMargin,
    resultText =
      """import tests.{Foo, toChar}
        |
        |object Test {
        |  val foo = Foo('z')
        |  foo.toChar
        |}
        |
        |package tests:
        |  final case class Foo(ch: Char)
        |
        |  extension (foo: Foo)
        |    def toChar: Char = foo.ch
        |end tests
        |""".stripMargin,
    item = "toChar",
    invocationCount = 2
  )

  def testExtensionMethodInsideGiven(): Unit = doCompletionTest(
    fileText =
      s"""import tests.Foo
         |
         |object Test {
         |  val foo = Foo('z')
         |  foo.toC$CARET
         |}
         |
         |package tests:
         |  final case class Foo(ch: Char)
         |
         |  object Extensions5:
         |    given ops: AnyRef with
         |      extension (foo: Foo)
         |        def toChar: Char = foo.ch
         |end tests
         |""".stripMargin,
    resultText =
      """import tests.Extensions5.ops
        |import tests.Foo
        |
        |object Test {
        |  val foo = Foo('z')
        |  foo.toChar
        |}
        |
        |package tests:
        |  final case class Foo(ch: Char)
        |
        |  object Extensions5:
        |    given ops: AnyRef with
        |      extension (foo: Foo)
        |        def toChar: Char = foo.ch
        |end tests
        |""".stripMargin,
    item = "toChar",
    invocationCount = 2
  )

  def testExtensionMethodInsideTopLevelGiven(): Unit = doCompletionTest(
    fileText =
      s"""import tests.Foo
         |
         |object Test {
         |  val foo = Foo('z')
         |  foo.toC$CARET
         |}
         |
         |package tests:
         |  final case class Foo(ch: Char)
         |
         |  given ops: AnyRef with
         |    extension (foo: Foo)
         |      def toChar: Char = foo.ch
         |end tests
         |""".stripMargin,
    resultText =
      """import tests.{Foo, ops}
        |
        |object Test {
        |  val foo = Foo('z')
        |  foo.toChar
        |}
        |
        |package tests:
        |  final case class Foo(ch: Char)
        |
        |  given ops: AnyRef with
        |    extension (foo: Foo)
        |      def toChar: Char = foo.ch
        |end tests
        |""".stripMargin,
    item = "toChar",
    invocationCount = 2
  )

  def testTopLevelTypeAliasTest(): Unit = doCompletionTest(
    s"""package a {
       |  type Foobar = Int
       |}
       |package b {
       |  type T = Foob$CARET
       |}
       |""".stripMargin,
    s"""package a {
       |  type Foobar = Int
       |}
       |package b {
       |
       |  import a.Foobar
       |
       |  type T = Foobar
       |}
       |""".stripMargin,
    item = "Foobar")

  def testEnumTypeTest(): Unit = doCompletionTest(
    s"""package a {
       |  enum Foobar
       |}
       |package b {
       |  type T = Foob$CARET
       |}
       |""".stripMargin,
    s"""package a {
       |  enum Foobar
       |}
       |package b {
       |
       |  import a.Foobar
       |
       |  type T = Foobar
       |}
       |""".stripMargin,
    item = "Foobar")

  def testEnumTermTest(): Unit = doCompletionTest(
    s"""package a {
       |  enum Foobar
       |}
       |package b {
       |  Foob$CARET
       |}
       |""".stripMargin,
    s"""package a {
       |  enum Foobar
       |}
       |package b {
       |
       |  import a.Foobar
       |
       |  Foobar
       |}
       |""".stripMargin,
    item = "Foobar")
}
