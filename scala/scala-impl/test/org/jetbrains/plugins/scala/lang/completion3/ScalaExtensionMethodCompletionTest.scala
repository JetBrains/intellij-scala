package org.jetbrains.plugins.scala.lang.completion3

import org.jetbrains.plugins.scala.lang.completion3.base.ScalaCompletionTestBase
import org.jetbrains.plugins.scala.util.runners.{RunWithScalaVersions, TestScalaVersion}
import org.junit.Assert.assertEquals
import org.junit.Test

@RunWithScalaVersions(Array(TestScalaVersion.Scala_3_Latest))
class ScalaExtensionMethodCompletionTest extends ScalaCompletionTestBase {

  @Test
  def testSimpleExtension(): Unit = doCompletionTest(
    s"""object Test {
       |  extension (s: String)
       |    def digits: Seq[Char] = s.filter(_.isDigit)
       |
       |  "foo123".di$CARET
       |}""".stripMargin,
    s"""object Test {
       |  extension (s: String)
       |    def digits: Seq[Char] = s.filter(_.isDigit)
       |
       |  "foo123".digits
       |}""".stripMargin,
    item = "digits"
  )

  @Test
  def testExtensionFromGiven(): Unit = doCompletionTest(
    s"""object math3:
       |  trait Ord[T]
       |
       |  trait Numeric[T] extends Ord[T]:
       |    extension (x: Int) def numeric: T = ???
       |
       |object Test3:
       |  import math3.Numeric
       |
       |  def to[T: Numeric](x: Int): T =
       |    x.num$CARET""".stripMargin,
    """object math3:
      |  trait Ord[T]
      |
      |  trait Numeric[T] extends Ord[T]:
      |    extension (x: Int) def numeric: T = ???
      |
      |object Test3:
      |  import math3.Numeric
      |
      |  def to[T: Numeric](x: Int): T =
      |    x.numeric""".stripMargin,
    item = "numeric"
  )

  @Test
  def testFromImplicitScope(): Unit = doCompletionTest(
    s"""class MyList[+T]
       |
       |object MyList:
       |  def apply[A](a: A*): MyList[A] = ???
       |
       |  extension [T](xs: MyList[MyList[T]])
       |    def flatten: MyList[T] = ???
       |
       |object Test {
       |  MyList(MyList(1, 2), MyList(3, 4)).fl$CARET
       |}""".stripMargin,
    """class MyList[+T]
      |
      |object MyList:
      |  def apply[A](a: A*): MyList[A] = ???
      |
      |  extension [T](xs: MyList[MyList[T]])
      |    def flatten: MyList[T] = ???
      |
      |object Test {
      |  MyList(MyList(1, 2), MyList(3, 4)).flatten
      |}""".stripMargin,
  "flatten")

  @Test
  def testOnlyApplicableExtensionMethodIsSuggested_SCL_25583(): Unit = {
    val (_, items) = activeLookupWithItems(
      s"""import scala.quoted.*
         |
         |def test(using quotes: Quotes)(tp: quotes.reflect.TypeRef): Unit =
         |  import quotes.reflect.*
         |  tp.na$CARET""".stripMargin
    )

    assertEquals(1, items.count(_.getLookupString == "name"))
  }
}
