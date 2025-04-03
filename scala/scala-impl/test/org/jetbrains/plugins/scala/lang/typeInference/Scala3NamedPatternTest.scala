package org.jetbrains.plugins.scala.lang.typeInference

import junit.framework.TestCase
import org.jetbrains.plugins.scala.util.GeneratedTestSuiteFactory
import org.jetbrains.plugins.scala.util.GeneratedTestSuiteFactory.SimpleTestData
import org.jetbrains.plugins.scala.{ScalaVersion, TypecheckerTests}
import org.junit.experimental.categories.Category


// SCL-21799
@Category(Array(classOf[TypecheckerTests]))
class Scala3NamedPatternTest extends TestCase

// TODO: add compiler checking test
object Scala3NamedPatternTest extends GeneratedTestSuiteFactory.withHighlightingTest(ScalaVersion.Latest.Scala_3_7) {
  lazy val testData: Seq[SimpleTestData] = Seq(
    """// namedTuple
      |type Foo = (int: Int, bool: Boolean)
      |val foo = (int = 1, bool = true)
      |
      |{
      |  val (int = i, bool = b) = foo
      |
      |  val _i: Int = i
      |  val _b: Boolean = b
      |}
      |
      |{
      |  val (bool = b, int = i) = foo
      |
      |  val _i: Int = i
      |  val _b: Boolean = b
      |}
      |
      |{
      |  val (int = i) = foo
      |
      |  val _i: Int = i
      |}
      |
      |{
      |  val (bool = b) = foo
      |
      |  val _b: Boolean = b
      |}
      |""".stripMargin,
    s"""// pureCaseClass
       |case class Foo(int: Int, bool: Boolean)
       |val foo = Foo(1, true)
       |
       |$fooTester
       |""".stripMargin,
    s"""// returnNamedTuple
       |type Custom = (int: Int, bool: Boolean)
       |
       |object Foo {
       |  def unapply(s: String): Custom = ???
       |}
       |
       |val foo = "blub"
       |
       |$fooTester
       |""".stripMargin,
    s"""// returnCaseClass
       |case class Custom(int: Int, bool: Boolean)
       |
       |object Foo {
       |  def unapply(s: String): Custom = ???
       |}
       |
       |val foo = "blub"
       |
       |$fooTester
       |""".stripMargin,
    s"""// returnSomeNamedTuple
       |type Custom = (int: Int, bool: Boolean)
       |
       |object Foo {
       |  def unapply(s: String): Some[Custom] = ???
       |}
       |
       |val foo = "blub"
       |
       |$fooTester
       |""".stripMargin,
    s"""// returnSomeCaseClass
       |case class Custom(int: Int, bool: Boolean)
       |
       |object Foo {
       |  def unapply(s: String): Some[Custom] = ???
       |}
       |
       |val foo = "blub"
       |
       |$fooTester
       |""".stripMargin,
    s"""// failDuplicatedNamesNamedTuples
       |
       |val (foo = _, foo = _) = (foo = 1, bar = true) // Error
       |
       |""".stripMargin,
    s"""// failDuplicatedNamesCaseClass
       |case class Foo(foo: Int, bar: Boolean)
       |
       |val Foo(foo = _, foo = _) = Foo(1, true) // Error
       |
       |""".stripMargin,
    s"""// failUnknownNameNamedTuples
       |
       |val (foo = _) = (bar = 9) // Error
       |""".stripMargin,
    s"""// failUnknownNameCaseClass
       |case class Foo(foo: Int)
       |
       |val Foo(bar = _) = Foo(1) // Error
       |
       |""".stripMargin,
    s"""// noNamedPatternsSupported
       |class Foo(foo: Int, bar: Int)
       |object Foo {
       |  def unapply(s: String): Foo = ???
       |}
       |
       |val Foo(bar = _) = Foo(1, 2) // Error
       |
       |""".stripMargin,
  ).map(testDataFromCode)

  private def fooTester: String =
    """
      |{
      |  val Foo(int = i, bool = b) = foo
      |
      |  val _i: Int = i
      |  val _b: Boolean = b
      |}
      |
      |{
      |  val Foo(bool = b, int = i) = foo
      |
      |  val _i: Int = i
      |  val _b: Boolean = b
      |}
      |
      |{
      |  val Foo(int = i) = foo
      |
      |  val _i: Int = i
      |}
      |
      |{
      |  val Foo(bool = b) = foo
      |
      |  val _b: Boolean = b
      |}
      |""".stripMargin
}