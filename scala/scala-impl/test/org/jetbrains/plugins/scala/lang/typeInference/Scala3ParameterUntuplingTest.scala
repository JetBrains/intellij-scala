package org.jetbrains.plugins.scala.lang.typeInference

import org.jetbrains.plugins.scala.{LatestScalaVersions, ScalaVersion}

class Scala3ParameterUntuplingTest extends TypeInferenceTestBase {
  override protected def supportedIn(version: ScalaVersion): Boolean =
    version >= LatestScalaVersions.Scala_3_0

  def testSimple(): Unit = doTest(
    s"""
       |object A {
       |  val xs: List[(Int, Int)] = ???
       |  ${START}xs.map { (x, y) => x + y }$END
       |}
       |//List[Int]
       |""".stripMargin
  )

  def testUnderscores(): Unit = doTest(
    s"""
       |object A {
       |  val xs: List[(Int, Int)] = ???
       |  ${START}xs.map(_ + _)$END
       |}
       |//List[Int]
       |""".stripMargin
  )

  def testSubClassParams(): Unit = doTest(
    s"""
       |object A {
       |  trait Baz
       |  trait Qux
       |  trait R
       |  trait Foo extends Baz
       |  trait Bar extends Qux
       |  val xs: List[(Foo, Bar)] = ???
       |  def combine(x: Baz, y: Qux): R = ???
       |  ${START}xs.map(combine)$END
       |}
       |//List[A.R]
       |""".stripMargin
  )

  def testMethodReference(): Unit = doTest(
    s"""
       |object A {
       |  val xs: List[(Int, Int)] = ???
       |  def combine(x: Int, y: Int): Int = x + y
       |  ${START}xs.map(combine)$END
       |}
       |//List[Int]
       |""".stripMargin
  )

  def testNonLiteralNeg(): Unit = checkHasErrorAroundCaret(
    s"""
       |object A {
       |  val xs: List[(Int, Int)] = ???
       |  val combiner: (Int, Int) => Int = ???
       |  xs.ma${CARET}p(combiner)
       |}
       |""".stripMargin
  )

  def testUntuplingNoAnnotations(): Unit = checkTextHasNoErrors(
    """
      |object A {
      |  val xs: List[(Int, Int)] = ???
      |  xs.map { (x, y) => x + y }
      |}
      |""".stripMargin
  )

  def testUntuplingCorrectAnnotations(): Unit = checkTextHasNoErrors(
    """
      |object A {
      |  val xs: List[(Int, Int)] = ???
      |  xs.map { (x: Int, y: Int) => x + y }
      |}
      |""".stripMargin
  )

  def testUntuplingWrongAnnotations(): Unit = checkHasErrorAroundCaret(
    s"""
       |object A {
       |  val xs: List[(Int, Int)] = ???
       |  xs.map { (x: S${CARET}tring, y: Int) => x + y }
       |}
       |""".stripMargin
  )
}
