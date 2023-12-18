package org.jetbrains.plugins.scala.lang.typeInference

import junit.framework.TestCase
import org.jetbrains.plugins.scala.util.GeneratedTestSuiteFactory
import org.jetbrains.plugins.scala.{ScalaVersion, TypecheckerTests}
import org.junit.experimental.categories.Category

// SCL-21799
@Category(Array(classOf[TypecheckerTests]))
class Scala3UnapplySeqTest extends TestCase

object Scala3UnapplySeqTest extends GeneratedTestSuiteFactory.withHighlightingTest(ScalaVersion.Latest.Scala_3_3) {
  // See https://docs.scala-lang.org/scala3/reference/changed-features/pattern-matching.html#
  lazy val testData: Seq[TestData] = Seq(
    // ============= Direct Sequence match =============
    s"""
       |// directSequenceMatchWithSeq
       |object A:
       |  def unapplySeq(i: Int): Seq[Boolean] = ???
       |
       |$tester_bs
       |""".stripMargin,
    s"""
       |// directSequenceMatchCustomWithLengthCompare
       |class Custom:
       |  def lengthCompare(len: Int): Int = ???
       |  def apply(i: Int): Boolean = ???
       |  def drop(n: Int): scala.Seq[Boolean] = ???
       |  def toSeq: scala.Seq[Boolean] = ???
       |
       |object A:
       |  def unapplySeq(i: Int): Custom = ???
       |
       |
       |$tester_bs
       |""".stripMargin,
    s"""
       |// directSequenceMatchCustomWithLength
       |class Custom:
       |  def length: Int = ???
       |  def apply(i: Int): Boolean = ???
       |  def drop(n: Int): scala.Seq[Boolean] = ???
       |  def toSeq: scala.Seq[Boolean] = ???
       |
       |object A:
       |  def unapplySeq(i: Int): Custom = ???
       |
       |$tester_bs
       |""".stripMargin,
    s"""
       |//directSequenceMatchWithTypeParameters
       |object A:
       |  def unapplySeq[X](x: X): Seq[X] = ???
       |
       |def test[Z](z: Z): Unit =
       |  val A(x, y) = z
       |  val _x: Z = x
       |  val _y: Z = y
       |""".stripMargin,
    // ============= Direct Product-Sequence match =============
    s"""
       |// directProductSequenceMatchWithSeqAndTuple
       |object A:
       |  def unapplySeq(i: Int): (String, Int, Seq[Boolean]) = ???
       |
       |$tester_s_i_bs
       |""".stripMargin,
    s"""
       |// directProductSequenceMatchCustom
       |class Custom extends scala.Product1[String]:
       |  def canEqual(that: Any): Boolean = true
       |  val _1 : String = ???
       |  val _2 : Int = ???
       |  val _3 : Seq[Boolean] = ???
       |
       |object A:
       |  def unapplySeq(i: Int): Custom = ???
       |
       |
       |$tester_s_i_bs
       |""".stripMargin,
    s"""
      |// directSequenceMatchBeforeProductSequenceMatch
      |class Custom extends scala.Product1[String]:
      |  def canEqual(that: Any): Boolean = true
      |  def length: Int = ???
      |  def apply(i: Int): Boolean = ???
      |  def drop(n: Int): scala.Seq[Boolean] = ???
      |  def toSeq: scala.Seq[Boolean] = ???
      |
      |  val _1 : String = ???
      |  val _2 : Int = ???
      |  val _3 : Seq[Unit] = ???
      |
      |object A:
      |  def unapplySeq(i: Int): Custom = ???
      |
      |$tester_bs
      |""".stripMargin,
//    """
//      |// noDirectProductSequenceMatchBecauseProductIsMissing
//      |class Custom:
//      |  val _1 : String = ???
//      |  val _2 : Int = ???
//      |  val _3 : Seq[Unit] = ???
//      |
//      |object A:
//      |  def unapplySeq(i: Int): Custom = ???
//      |
//      |val A(s, i) = 1 // Error
//      |
//      |""".stripMargin,

    // ============= Indirect Sequence match =============
    s"""
       |// indirectSequenceMatchWithOptionSeq
       |object A:
       |  def unapplySeq(i: Int): Option[Seq[Boolean]] = ???
       |
       |$tester_bs
       |""".stripMargin,
    s"""
       |// indirectSequenceMatchCustomWithLengthCompare
       |class Custom:
       |  def lengthCompare(len: Int): Int = ???
       |  def apply(i: Int): Boolean = ???
       |  def drop(n: Int): scala.Seq[Boolean] = ???
       |  def toSeq: scala.Seq[Boolean] = ???
       |
       |class Unapplied:
       |  def isEmpty = false
       |  def get: Custom = ???
       |
       |object A:
       |  def unapplySeq(i: Int): Unapplied = new Unapplied
       |
       |
       |$tester_bs
       |""".stripMargin,
    s"""
       |// indirectSequenceMatchCustomWithLength
       |class Custom:
       |  def length: Int = ???
       |  def apply(i: Int): Boolean = ???
       |  def drop(n: Int): scala.Seq[Boolean] = ???
       |  def toSeq: scala.Seq[Boolean] = ???
       |
       |class Unapplied:
       |  def isEmpty = false
       |  def get: Custom = ???
       |
       |object A:
       |  def unapplySeq(i: Int): Unapplied = ???
       |
       |$tester_bs
       |""".stripMargin,
    // ============= Indirect Product-Sequence match =============
    s"""
       |// indirectProductSequenceMatchWithSomeSeqAndTuple
       |object A:
       |  def unapplySeq(i: Int): Some[(String, Int, Seq[Boolean])] = ???
       |
       |$tester_s_i_bs
       |""".stripMargin,
    s"""
       |// indirectProductSequenceMatchCustom
       |class Custom extends scala.Product1[String]:
       |  def canEqual(that: Any): Boolean = true
       |  val _1 : String = ???
       |  val _2 : Int = ???
       |  val _3 : Seq[Boolean] = ???
       |
       |class Unapplied:
       |  def isEmpty = false
       |  def get: Custom = ???
       |
       |object A:
       |  def unapplySeq(i: Int): Unapplied = ???
       |
       |
       |$tester_s_i_bs
       |""".stripMargin,
    s"""
       |// indirectProductSequenceMatchCustomWithTypeParameters
       |class Custom[X, Y] extends scala.Product1[X]:
       |  def canEqual(that: Any): Boolean = true
       |  val _1 : X = ???
       |  val _2 : Int = ???
       |  val _3 : Seq[Y] = ???
       |
       |class Unapplied[X, Y]:
       |  def isEmpty = false
       |  def get: Custom[X, Y] = ???
       |
       |object A:
       |  def unapplySeq[X, Y](i: (X, Y)): Unapplied[X, Y] = ???
       |
       |def test[X, Y](tuple: (X, Y)) = {
       |  {
       |    val A(x, i) = tuple
       |    val _x: X = x
       |    val _i: Int = i
       |  }
       |  {
       |    val A(x, i, y, yy*) = tuple
       |    val _x: X = x
       |    val _i: Int = i
       |    val _y: Y = y
       |    val _yy: Seq[Y] = yy
       |  }
       |}
       |""".stripMargin,

//    s"""
//       |// indirectSequenceMatchBeforeProductSequenceMatch
//       |class Custom extends scala.Product1[String]:
//       |  def canEqual(that: Any): Boolean = true
//       |  def length: Int = ???
//       |  def apply(i: Int): Boolean = ???
//       |  def drop(n: Int): scala.Seq[Boolean] = ???
//       |  def toSeq: scala.Seq[Boolean] = ???
//       |
//       |  val _1 : String = ???
//       |  val _2 : Int = ???
//       |  val _3 : Seq[Unit] = ???
//       |
//       |class Unapplied:
//       |  def isEmpty = false
//       |  def get: Custom = ???
//       |
//       |object A:
//       |  def unapplySeq(i: Int): Unapplied = ???
//       |
//       |$tester_bs
//       |""".stripMargin,
    s"""
       |// directSequenceMatchBeforeIndirectSequenceMatch
       |class Custom:
       |  def length: Int = ???
       |  def apply(i: Int): String = ???
       |  def drop(n: Int): scala.Seq[String] = ???
       |  def toSeq: scala.Seq[String] = ???
       |
       |
       |class Unapplied:
       |  def length: Int = ???
       |  def apply(i: Int): Boolean = ???
       |  def drop(n: Int): scala.Seq[Boolean] = ???
       |  def toSeq: scala.Seq[Boolean] = ???
       |
       |  def isEmpty = false
       |  def get: Custom = ???
       |
       |object A:
       |  def unapplySeq(i: Int): Unapplied = ???
       |
       |$tester_bs
       |""".stripMargin,
    s"""
       |// directProductMatchBeforeIndirectSequenceMatch
       |trait X
       |trait Y
       |
       |class Unapplied extends scala.Product2[X, Y]:
       |  def canEqual(that: Any): Boolean = true
       |
       |  def isEmpty = false
       |  def get: Seq[Boolean] = ???
       |
       |  def _1: X = ???
       |  def _2: Y = ???
       |  def _3: Seq[Int] = ???
       |
       |object A:
       |  def unapplySeq(i: Int): Unapplied = ???
       |
       |val A() = 1
       |
       |
       |{
       |  val A(rest*) = 1
       |  val _rest: Seq[Boolean] = rest
       |}
       |
       |{
       |  val A(b) = 1
       |  val _b: Boolean = b
       |}
       |
       |{
       |  val A(x, y) = 1
       |  val _x: X = x
       |  val _y: Y = y
       |}
       |
       |{
       |  val A(x, y, i, rest*) = 1
       |  val _x: X = x
       |  val _y: Y = y
       |  val _i: Int = i
       |  val _rest: Seq[Int] = rest
       |}
       |
       |""".stripMargin
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
      |  val A(rest*) = 1
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
      |  val A(b1, b2, rest: _*) = 1
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
      |  val A(s, i, rest*) = 1
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
      |  val A(s, i, b, rest: _*) = 1
      |  val _s: String = s
      |  val _i: Int = i
      |  val _b: Boolean = b
      |  val _rest: Seq[Boolean] = rest
      |}
      |""".stripMargin
}
