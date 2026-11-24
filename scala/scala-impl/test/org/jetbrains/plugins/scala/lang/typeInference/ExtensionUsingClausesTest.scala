package org.jetbrains.plugins.scala.lang.typeInference

import org.jetbrains.plugins.scala.util.GeneratedParameterizedTestFactory.SimpleTestData
import org.jetbrains.plugins.scala.util.{GeneratedHighlightingParameterizedTest, GeneratedParameterizedTestFactory}
import org.jetbrains.plugins.scala.{LatestScalaVersions, ScalaVersion, TypecheckerTests}
import org.junit.experimental.categories.Category

/** SCL-26341. The same cases are compiled by CheckExtensionUsingClausesTest. */
object ExtensionUsingClausesTest {
  lazy val testData: Seq[SimpleTestData] = Seq(
    """
      |// leadingExtensionUsingClause
      |extension (using Int)(i: Int)
      |  def foo: Int = i
      |given Int = 1
      |
      |val selected: Int = 2.foo
      |val inferred: Int = foo(2)
      |val explicit: Int = foo(using 3)(2)
      |""".stripMargin,
    """
      |// extensionUsingClausesBeforeAndAfterReceiver
      |extension (using Int)(i: Int)(using Long)
      |  def foo[T](using Boolean): Unit = ???
      |given Int = 1
      |given Long = 1L
      |given Boolean = true
      |
      |2.foo[Int]
      |2.foo(using 2L)[Int](using false)
      |2.foo[Int](using false)
      |2.foo(using 2L)[Int]
      |""".stripMargin,
    """
      |// multipleLeadingExtensionUsingClauses
      |extension (using Int)(using String)(i: Int)(using Long)
      |  def foo[T](using Boolean): Unit = ???
      |given Int = 1
      |given String = "s"
      |
      |2.foo(using 2L)[Int](using true)
      |""".stripMargin,
    """
      |// genericLeadingExtensionUsingClause
      |extension [A](using List[A])(a: A)(using Option[A])
      |  def foo[B](b: B): (A, B) = (a, b)
      |given List[Int] = Nil
      |given Option[Int] = None
      |
      |val inferred: (Int, String) = 2.foo("s")
      |val explicit: (Int, String) = 2.foo(using Some(1))[String]("s")
      |""".stripMargin,
    """
      |// leadingExtensionUsingClauseWithinSameExtension
      |extension (using Int)(i: Int)(using Long)
      |  def foo[T](using Boolean): Int = i
      |  def bar(using Boolean): Int = foo[Int]
      |  def baz: Int = foo[Int](using true)
      |""".stripMargin,
    """
      |// missingLeadingExtensionUsingClause
      |extension (using String)(i: Int)(using Long)
      |  def foo[T](using Boolean): Unit = ???
      |given Long = 1L
      |given Boolean = true
      |
      |2.foo[Int] // Error
      |2.foo(using 1L)[Int](using true) // Error
      |""".stripMargin,
    """
      |// inferredAndExplicitExtensionArguments
      |extension (x: Int)(using Long)
      |  def foo[T](using Boolean): Unit = ???
      |given Long = 1L
      |given Boolean = true
      |
      |2.foo[Int]
      |2.foo(using 1L)[Int](using true)
      |2.foo[Int](using true)
      |2.foo(using 1L)[Int]
      |""".stripMargin,
    """
      |// explicitExtensionArgumentsWithoutGivens
      |extension (x: Int)(using Long)
      |  def foo[T](using Boolean): Unit = ???
      |
      |2.foo(using 1L)[Int](using true)
      |""".stripMargin,
    """
      |// extensionReceiverTypeInference
      |extension [A](x: A)(using List[A])
      |  def foo[B](b: B)(using Option[B]): (A, B) = (x, b)
      |given List[Int] = Nil
      |given Option[String] = None
      |
      |val inferred: (Int, String) = 2.foo("s")
      |val explicit: (Int, String) = 2.foo(using List(1))[String]("s")(using None)
      |""".stripMargin,
    """
      |// extensionCallsWithinSameExtension
      |extension (x: Int)(using Long)
      |  def foo[T](using Boolean): Unit = ???
      |  def bar(using Boolean): Unit = foo[Int]
      |  def baz: Unit = foo[Int](using true)
      |""".stripMargin,
    """
      |// extensionWithoutMethodParameters
      |extension (x: Int)(using Long)
      |  def foo: Int = x
      |given Long = 1L
      |
      |val inferred: Int = 2.foo
      |val explicit: Int = 2.foo(using 1L)
      |""".stripMargin,
    """
      |// overloadedExtensionMethod
      |extension (x: Int)(using Long)
      |  def foo[T](using Boolean): Int = x
      |  def foo[T](s: String)(using Boolean): String = s
      |given Long = 1L
      |given Boolean = true
      |
      |val inferred: Int = 2.foo[Int](using true) // Error
      |val withArgument: String = 2.foo[Int]("s")
      |val explicit: Int = 2.foo(using 1L)[Int](using true)
      |""".stripMargin,
    """
      |// incorrectExtensionArguments
      |extension (x: Int)(using Long)
      |  def foo[T](using Boolean): Unit = ???
      |
      |2.foo(using true)[Int](using true) // Error
      |2.foo(using 1L)[Int](using 1L) // Error
      |2.foo[Int] // Error
      |""".stripMargin
  ).map(GeneratedParameterizedTestFactory.testDataFromCode)
}

@Category(Array(classOf[TypecheckerTests]))
abstract class ExtensionUsingClausesTestBase(scalaVersion: ScalaVersion)
  extends GeneratedHighlightingParameterizedTest(scalaVersion) {
  override type TD = SimpleTestData
  override def testData: Seq[SimpleTestData] = ExtensionUsingClausesTest.testData
  override protected def supportedIn(version: ScalaVersion): Boolean = version == scalaVersion
}

final class ExtensionUsingClausesTest_3_3 extends ExtensionUsingClausesTestBase(LatestScalaVersions.Scala_3_3)
final class ExtensionUsingClausesTest_3_9 extends ExtensionUsingClausesTestBase(LatestScalaVersions.Scala_3_9)
