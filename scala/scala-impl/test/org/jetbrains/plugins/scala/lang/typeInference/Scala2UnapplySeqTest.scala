package org.jetbrains.plugins.scala.lang.typeInference

import junit.framework.TestCase
import org.jetbrains.plugins.scala.util.GeneratedTestSuiteFactory
import org.jetbrains.plugins.scala.{ScalaVersion, TypecheckerTests}
import org.junit.experimental.categories.Category

// SCL-21799
@Category(Array(classOf[TypecheckerTests]))
class Scala2UnapplySeqTest extends TestCase

object Scala2UnapplySeqTest extends GeneratedTestSuiteFactory.withHighlightingTest(ScalaVersion.Latest.Scala_2_13) {
  // https://www.scala-lang.org/files/archive/spec/2.13/08-pattern-matching.html#pattern-sequences
  lazy val testData: Seq[TestData] = Seq(
    s"""
       |// seqWithoutExtractorType
       |object A {
       |  def unapplySeq(i: Int): Seq[Boolean] = ???
       |}
       |
       |val A(_) = 1    // Error
       |""".stripMargin,
    s"""
       |// seqInExtractorType
       |class Result(val get: Seq[Boolean]) {
       |  def isEmpty = false
       |}
       |
       |object A {
       |  def unapplySeq(i: Int): Result = ???
       |}
       |
       |$tester_bs
       |""".stripMargin,
    s"""
       |// tupleSeqWithoutExtractorType
       |object A {
       |  def unapplySeq(i: Int): (String, Int, Seq[Boolean]) = ???
       |}
       |
       |val A(_) = 1    // Error
       |""".stripMargin,
    s"""
       |// customExtractorType
       |class Custom {
       |  def _1: String = ???
       |  def _2: Int = ???
       |  def _3: Seq[Boolean] = ???
       |}
       |
       |class Unapplied {
       |  def isEmpty = false
       |  def get: Custom = ???
       |}
       |
       |object A {
       |  def unapplySeq(i: Int): Unapplied = new Unapplied
       |}
       |
       |$tester_s_i_bs
       |""".stripMargin,
    s"""
       |// customExtractorTypeWithoutSeq
       |class Custom {
       |  def _1: String = ???
       |  def _2: Int = ???
       |  def _3: Boolean = ???
       |}
       |
       |class Unapplied {
       |  def isEmpty = false
       |  def get: Custom = ???
       |}
       |
       |object A {
       |  def unapplySeq(i: Int): Unapplied = new Unapplied
       |}
       |
       |val A(s, i, b) = 1    // Error
       |""".stripMargin,
    s"""
       |// tupleSeqExtractorType
       |object A {
       |  def unapplySeq(i: Int): Some[(String, Int, Seq[Boolean])] = ???
       |}
       |
       |$tester_s_i_bs
       |""".stripMargin,
    s"""
       |// tupleSeqExtractorTypeWithTypeParameters
       |class Custom[X, Y] {
       |  val _1 : X = ???
       |  val _2 : Int = ???
       |  val _3 : Seq[Y] = ???
       |}
       |class Unapplied[X, Y] {
       |  def isEmpty = false
       |  def get: Custom[X, Y] = ???
       |}
       |
       |object A {
       |  def unapplySeq[X, Y](i: (X, Y)): Unapplied[X, Y] = ???
       |}
       |
       |def test[X, Y](tuple: (X, Y)) = {
       |  {
       |    val A(x, i) = tuple
       |    val _x: X = x
       |    val _i: Int = i
       |  }
       |  {
       |    val A(x, i, y, yy@_*) = tuple
       |    val _x: X = x
       |    val _i: Int = i
       |    val _y: Y = y
       |    val _yy: Seq[Y] = yy
       |  }
       |}
       |""".stripMargin,
    s"""
      |// accessorsBeforeSeq
      |class TupleAsSeq extends Seq[Int] {
      |  override def apply(i: Int): Int = ???
      |  override def length: Int = ???
      |  override def iterator: Iterator[Int] = ???
      |
      |  def _1: String = ???
      |  def _2: Int = ???
      |  def _3: Seq[Boolean] = ???
      |}
      |
      |object A {
      |  def unapplySeq(i: Int): Some[TupleAsSeq] = ???
      |}
      |
      |$tester_s_i_bs
      |""".stripMargin,
  ).map(testDataFromCode)

  private def tester_bs: String =
    """
      |
      |val A() = 1
      |
      |{
      |  val A(b) = 1
      |  val _b: Boolean = b
      |}
      |
      |{
      |  val A(rest@_*) = 1
      |  val _rest: Seq[Boolean] = rest
      |}
      |
      |{
      |  val A(b1, b2) = 1
      |  val _b1: Boolean = b1
      |  val _b2: Boolean = b2
      |}
      |
      |{
      |  val A(b1, b2, rest@_*) = 1
      |  val _b1: Boolean = b1
      |  val _b2: Boolean = b2
      |  val _rest: Seq[Boolean] = rest
      |}
      |""".stripMargin

  private def tester_s_i_bs: String =
    """
      |{
      |  val A(s, i) = 1
      |  val _s: String = s
      |  val _i: Int = i
      |}
      |
      |{
      |  val A(s, i, rest@_*) = 1
      |  val _s: String = s
      |  val _i: Int = i
      |  val _rest: Seq[Boolean] = rest
      |}
      |
      |{
      |  val A(s, i, b) = 1
      |  val _s: String = s
      |  val _i: Int = i
      |  val _b: Boolean = b
      |}
      |
      |{
      |  val A(s, i, b1, b2) = 1
      |  val _s: String = s
      |  val _i: Int = i
      |  val _b1: Boolean = b1
      |  val _b2: Boolean = b2
      |}
      |
      |{
      |  val A(s, i, b, rest@_*) = 1
      |  val _s: String = s
      |  val _i: Int = i
      |  val _b: Boolean = b
      |  val _rest: Seq[Boolean] = rest
      |}
      |""".stripMargin
}
