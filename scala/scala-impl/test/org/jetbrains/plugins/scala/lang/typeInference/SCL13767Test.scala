package org.jetbrains.plugins.scala.lang.typeInference

import org.jetbrains.plugins.scala.TypecheckerTests
import org.jetbrains.plugins.scala.annotator.ScalaHighlightingTestLike
import org.jetbrains.plugins.scala.base.ScalaLightCodeInsightFixtureTestCase
import org.jetbrains.plugins.scala.util.runners.{MultipleScalaVersionsJUnit4Runner, RunWithScalaVersions, TestScalaVersion}
import org.junit.Test
import org.junit.experimental.categories.Category
import org.junit.runner.RunWith

@Category(Array(classOf[TypecheckerTests]))
@RunWith(classOf[MultipleScalaVersionsJUnit4Runner])
@RunWithScalaVersions(Array(
  TestScalaVersion.Scala_2_11,
  TestScalaVersion.Scala_2_12,
  TestScalaVersion.Scala_2_13,
  TestScalaVersion.Scala_3_Latest,
))
class SCL13767Test extends ScalaLightCodeInsightFixtureTestCase with ScalaHighlightingTestLike {
  //SCL-13767
  @Test
  def testSCL13767(): Unit = {
    assertNoErrors(
      """object Wrapper {
        |  trait Aux[T, U]
        |
        |  implicit def aux[T <: {type Type = U}, U]: Aux[T {type Type = U}, U] = ???
        |
        |  type Test <: {type Type = Int}
        |
        |  def test[U](implicit ev: Aux[Test, U]): U = ???
        |
        |  test
        |}
        |""".stripMargin
    )
  }
}
