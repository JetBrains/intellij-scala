package org.jetbrains.plugins.scala.lang.typeInference

import org.jetbrains.plugins.scala.{LatestScalaVersions, ScalaVersion, TypecheckerTests}
import org.jetbrains.plugins.scala.base.ScalaLightCodeInsightFixtureTestCase
import org.junit.experimental.categories.Category

@Category(Array(classOf[TypecheckerTests]))
class EmptySupersNewTemplateScala3Test extends ScalaLightCodeInsightFixtureTestCase {
  override protected def supportedIn(version: ScalaVersion) = version >= LatestScalaVersions.Scala_3

  def testSCL22565(): Unit = checkTextHasNoErrors(
    """
      |sealed trait Test:
      |  def pure[A](value: A): Unit
      |
      |object Live:
      |  def live: Test = new { def pure[A](value: A) = () }
      |""".stripMargin
  )
}
