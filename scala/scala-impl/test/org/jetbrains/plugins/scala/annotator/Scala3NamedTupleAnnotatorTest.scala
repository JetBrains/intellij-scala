package org.jetbrains.plugins.scala.annotator

import org.jetbrains.plugins.scala.ScalaVersion

class Scala3NamedTupleAnnotatorTest extends ScalaHighlightingTestBase{

  override protected def supportedIn(version: ScalaVersion): Boolean =
    version >= ScalaVersion.Latest.Scala_3_7

  def testAndNamedTupleBounds(): Unit = assertNoErrors(
    """
      |type NT = NamedTuple.Concat[(hi: Int), (str: String)]
      |""".stripMargin
  )

  // SCL-23684
  def testSoeOnReverse(): Unit = assertNoErrors(
    """
      |object NamedTupleExamples:
      |  type NT1 = (name: String, age: Int, city: String)
      |  type NT2 = (country: String, hobby: String)
      |
      |  type Reversed = NamedTuple.Reverse[NT1]
      |
      |  def exampleReverse(namedTuple: NT1): Reversed =
      |    namedTuple.reverse
      |
      |""".stripMargin
  )
}
