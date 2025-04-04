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
}
