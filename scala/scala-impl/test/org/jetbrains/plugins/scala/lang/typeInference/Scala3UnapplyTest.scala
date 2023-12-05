package org.jetbrains.plugins.scala.lang.typeInference

import junit.framework.TestCase
import org.jetbrains.plugins.scala.util.GeneratedTestSuiteFactory
import org.jetbrains.plugins.scala.{ScalaVersion, TypecheckerTests}
import org.junit.experimental.categories.Category

// SCL-21799
@Category(Array(classOf[TypecheckerTests]))
class Scala3UnapplyTest extends TestCase

object Scala3UnapplyTest extends GeneratedTestSuiteFactory.withHighlightingTest(ScalaVersion.Latest.Scala_3_3) {
  // See https://docs.scala-lang.org/scala3/reference/changed-features/pattern-matching.html#
  lazy val testData: Seq[TestData] = Seq(
    // ============= Boolean Match =============
    """
      |// booleanExtractor
      |object A:
      |  def unapply(i: Int): Boolean = true
      |object B:
      |  def unapply(i: Int): true = true
      |
      |val A() = 1
      |val B() = 2
      |""".stripMargin,
  ).map(testDataFromCode)
}
