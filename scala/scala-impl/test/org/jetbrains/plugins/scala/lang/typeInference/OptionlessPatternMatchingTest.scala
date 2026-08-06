package org.jetbrains.plugins.scala.lang.typeInference

import org.jetbrains.plugins.scala.{LatestScalaVersions, ScalaVersion}

class OptionlessPatternMatchingTest extends TypeInferenceTestBase {
  override protected def supportedIn(version: ScalaVersion): Boolean =
    version >= LatestScalaVersions.Scala_3_0

  def testBooleanMatch(): Unit = checkTextHasNoErrors {
    """
      |object Extractor { def unapply(x: Int): Boolean = true }
      |
      |1 match { case Extractor() => () }
    """.stripMargin.trim
  }

  def testSingleMatch(): Unit = doTest {
    """
      |class Result { def isEmpty = false; def get: Int = 1 }
      |
      |object Extractor { def unapply(x: Int): Result = true }
      |
      |1 match { case Extractor(x) => /*start*/x/*end*/ }
      |//Int
    """.stripMargin.trim
  }

  def testProductMatch(): Unit = doTest {
    """
      |class Result extends Product {}
      |object Extractor { def unapply(x: Int): (Int, Long) = (1, 2L) }
      |
      |1 match { case Extractor(x, y) => /*start*/y/*end*/ }
      |//Long
    """.stripMargin.trim
  }

  def testNameBasedMatch(): Unit = doTest {
    """
      |class Type { val _1: Int = 1; val _2: Long = 2L }
      |
      |class Result { def isEmpty = false; def get: Type = new Type() }
      |
      |object Extractor { def unapply(x: Int): Result = new Result() }
      |
      |1 match { case Extractor(x, y) => /*start*/y/*end*/ }
      |//Long
    """.stripMargin.trim
  }

  def testSequenceMatch(): Unit = doTest {
    """
      |class Result {
      |  def length: Int = 2
      |  def apply(i: Int): Long = 2L
      |  def drop(n: Int): Seq[Long] = Seq.empty[Long]
      |  def toSeq: Seq[Long] = Seq[Long](1L, 2L)
      |}
      |
      |object Extractor { def unapplySeq(x: Int): Result = new Result() }
      |
      |1 match { case Extractor(x, y) => /*start*/y/*end*/ }
      |//Long
    """.stripMargin.trim
  }
}
