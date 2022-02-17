package org.jetbrains.plugins.scala.lang.completion3

import org.jetbrains.plugins.scala.base.libraryLoaders.{LibraryLoader, SourcesLoader}
import org.jetbrains.plugins.scala.{LatestScalaVersions, ScalaVersion}

class Scala3GlobalMemberCompletionTest extends ScalaCodeInsightTestBase {
  override protected def supportedIn(version: ScalaVersion) = version >= LatestScalaVersions.Scala_3_0

  override def getTestDataPath: String =
    s"${super.getTestDataPath}globalMember3"

  override def librariesLoaders: Seq[LibraryLoader] =
    super.librariesLoaders :+ SourcesLoader(getTestDataPath)

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
    time = 2
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
    time = 2
  )

}
