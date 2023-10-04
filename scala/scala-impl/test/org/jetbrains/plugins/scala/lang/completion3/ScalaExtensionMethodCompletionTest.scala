package org.jetbrains.plugins.scala.lang.completion3

import org.jetbrains.plugins.scala.ScalaVersion
import org.jetbrains.plugins.scala.lang.completion3.base.ScalaCompletionTestBase

class ScalaExtensionMethodCompletionTest extends ScalaCompletionTestBase {
  override protected def supportedIn(version: ScalaVersion): Boolean =
    version >= ScalaVersion.Latest.Scala_3_0

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
}
