package org.jetbrains.plugins.scala.lang.typeInference

import org.jetbrains.plugins.scala.ScalaVersion
import org.jetbrains.plugins.scala.base.ScalaLightCodeInsightFixtureTestCase

class Scala3NewTuplesTest extends ScalaLightCodeInsightFixtureTestCase {
  override def supportedIn(version: ScalaVersion): Boolean = version >= ScalaVersion.Latest.Scala_3_7

  // SCL-21578
  def testAllTupleOperations(): Unit = checkTextHasNoErrors(
    """
      |val tup: (1, 2, 3, 4) = ???
      |val x1: 4 = tup.size
      |val x2: 1 = tup.head
      |val x3: (2, 3, 4) = tup.tail
      |val x4: (Int, Boolean, Int, Int) = 3 *: true *: 5 *: 6 *: EmptyTuple
      |val x5: (Int, String, Int, Boolean, Int) = (1, "") ++ (4, true, 6)
      |val x7: (1, 2) = tup.take(2)
      |val x8: 3 = tup(2)
      |val x9: ((1, 2), (3, 4)) = tup.splitAt(2)
      |val xA: ((1, Char), (2, Char)) = tup.zip(('a', 'b'))
      |val xB: List[Int | Char] = (1, 'a', 2).toList
      |val xC: Array[AnyRef] = (1, 'a', 2).toArray
      |val xD: IArray[AnyRef] = (1, 'a', 2).toIArray
      |val xE: (Option[Int], Option[Char]) = (1, 'a').map[[X] =>> Option[X]]([T] => (t: T) => Some(t))
      |""".stripMargin
  )

  def testUnapply(): Unit = checkTextHasNoErrors(
    """
      |val (a, b) = (1, 2)
      |val (c, d) = 3 *: 4 *: EmptyTuple
      |
      |// Interoperability with Tuple2
      |val (e, f) = Tuple2(5, 6)
      |val Tuple2(g, h) = (7, 8)
      |""".stripMargin
  )

  // SCL-23462
  def testCons(): Unit = checkTextHasNoErrors(
    """
      |def combine[X <: Boolean, Xs <: (Int,Int)](p: X, ps: Xs) : (Boolean,Int, Int) =
      |   p *: ps
      |""".stripMargin
  )

  // SCL-23471
  def testNonEmptyConformance(): Unit = checkTextHasNoErrors(
    """
      |type T = (Int, Int, Int)
      |
      |val myTuple: T = (1, 2, 3)
      |val myNonEmptyTuple: NonEmptyTuple = myTuple
      |""".stripMargin
  )
}
