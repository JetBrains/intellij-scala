package org.jetbrains.plugins.scala.annotator

import org.jetbrains.plugins.scala.ScalaVersion
import org.jetbrains.plugins.scala.base.ScalaLightCodeInsightFixtureTestCase

class Scala3DependentFunTypeAnnotatorTest extends ScalaLightCodeInsightFixtureTestCase {
  override protected def supportedIn(version: ScalaVersion): Boolean = version.isScala3

  // SCL-24065
  def testCallingDependentFunType(): Unit = checkTextHasNoErrors(
    """
      |def test(f: (i: Int) => Int): Int = f(1)
      |""".stripMargin
  )
}
