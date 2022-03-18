package org.jetbrains.plugins.scala.lang.autoImport

import org.jetbrains.plugins.scala.{LatestScalaVersions, ScalaVersion}

final class ImportGivenImplicitInstanceFixTest extends ImportImplicitInstanceFixTestBase {
  override protected def supportedIn(version: ScalaVersion) = version >= LatestScalaVersions.Scala_3_0

  def testGivenDependency(): Unit = checkElementsToImport(
    s"""package tests
       |
       |package givens:
       |  given String = "foo"
       |  given stringHead(using s: String): Char = s.head
       |end implicits
       |
       |object Test:
       |  def char(using c: Char) = (c + 1).toChar
       |
       |  val ch = ${CARET}char
       |end Test
       |""".stripMargin,

    "tests.givens.stringHead",
  )

  def testGivenDependency2(): Unit = checkElementsToImport(
    s"""package tests
       |
       |import givens.stringHead
       |
       |package givens:
       |  given String = "foo"
       |  given stringHead(using s: String): Char = s.head
       |end implicits
       |
       |object Test:
       |  def char(using c: Char) = (c + 1).toChar
       |
       |  val ch = ${CARET}char
       |end Test
       |""".stripMargin,

    "tests.givens.given_String",
  )

  def testTwoArguments(): Unit = checkElementsToImport(
    s"""package tests
       |
       |object givens:
       |  given String = ???
       |  given i: Int = ???
       |  given b: Boolean = ???
       |end givens
       |
       |object Test:
       |  def foo[T](using str: String, flag: Boolean) = ???
       |
       |  ${CARET}foo
       |end Test
       |""".stripMargin,

    "tests.givens.given_String",
    "tests.givens.b"
  )

  def testGenericGiven(): Unit = checkElementsToImport(
    s"""package tests
       |
       |trait Foo[T]:
       |  def foo: T
       |
       |object givens:
       |  given Foo[String] = ???
       |  given optionFoo[T]: Foo[Option[T]] with
       |    def foo: Option[T] = ???
       |end givens
       |
       |object Test:
       |  def bar[T](using foo: Foo[T]) = ???
       |
       |  ${CARET}bar
       |end Test
       |""".stripMargin,

    "tests.givens.given_Foo_String",
    "tests.givens.optionFoo"
  )

  private def testCompoundGiven(call: String): Unit = checkElementsToImport(
    s"""package tests
       |
       |trait Foo:
       |  def foo: Int
       |
       |trait Bar:
       |  def bar: String
       |
       |package givens:
       |  given String = "foo"
       |  given someGivenInt: Int = 21
       |
       |  given Foo with Bar with
       |    def foo = summon[Int]
       |    def bar = summon[String]
       |end givens
       |
       |def onlyFoo(using foo: Foo) = foo.foo
       |def onlyBar(using bar: Bar) = bar.bar
       |def fooWithBar(using fooBar: Foo with Bar) = fooBar.foo + " " + fooBar.bar
       |def barWithFoo(using barFoo: Bar with Foo) = barFoo.bar + " " + barFoo.foo
       |
       |object Test:
       |  $CARET$call
       |end Test
       |""".stripMargin,

    "tests.givens.given_Foo_Bar"
  )

  def testCompoundGiven_onlyFoo(): Unit = testCompoundGiven("onlyFoo")

  def testCompoundGiven_onlyBar(): Unit = testCompoundGiven("onlyBar")

  def testCompoundGiven_fooWithBar(): Unit = testCompoundGiven("fooWithBar")

  def testCompoundGiven_barWithFoo(): Unit = testCompoundGiven("barWithFoo")

}
