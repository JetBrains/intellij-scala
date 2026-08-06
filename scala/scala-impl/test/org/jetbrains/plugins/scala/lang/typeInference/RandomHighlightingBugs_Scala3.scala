package org.jetbrains.plugins.scala.lang.typeInference

import org.jetbrains.plugins.scala.annotator.ScalaHighlightingTestLike
import org.jetbrains.plugins.scala.{ScalaVersion, TypecheckerTests}
import org.jetbrains.plugins.scala.base.ScalaLightCodeInsightFixtureTestCase
import org.junit.experimental.categories.Category

@Category(Array(classOf[TypecheckerTests]))
class RandomHighlightingBugs_Scala3
  extends ScalaLightCodeInsightFixtureTestCase
    with ScalaHighlightingTestLike
    with RandomHighlightingBugs_CommonTests {

  // 3.6 used for "deferred" givens primarily
  override protected def supportedIn(version: ScalaVersion): Boolean = version > ScalaVersion.Latest.Scala_3_6

  override def test_SCL24453_1(): Unit = {
    getFixture.addFileToProject("definitions.scala", SCL24453.CommonDefinitions)
    assertErrorsText(
      SCL24453.CodeExample1,
      // Note: this is the only error expected.
      // We are indirectly testing that there are no errors in other places,
      // emulating the code produced by the decompiler, though it's invalid when in source code
      // In particular we are testing that the ` jdbcFoo ` symbol is resolved everywhere
      """Error(dbConfigLazy,Reference to non-final lazy value `dbConfigLazy` is not allowed here)
        |""".stripMargin
    )
  }

  override def test_SCL24453_2(): Unit = {
    getFixture.addFileToProject("definitions.scala", SCL24453.CommonDefinitions)
    assertErrorsText(
      SCL24453.CodeExample2,
      // Note: this is the only error expected.
      // We are indirectly testing that there are no errors in other places,
      // emulating the code produced by the decompiler, though it's invalid when in source code
      // In particular we are testing that the ` jdbcFoo ` symbol is resolved everywhere
      """Error(dbConfigLazy,Reference to non-final lazy value `dbConfigLazy` is not allowed here)
        |""".stripMargin
    )
  }

  def testSCL22079(): Unit = {
    assertNoErrors(
      """trait Repro:
        |  trait Z[X[T] <: Y[T], Y[_]]
        |
        |  trait A[T]
        |  type B[T] <: A[T]
        |
        |  given smth: Z[B, A]
        |
        |trait Repro2:
        |  trait Z[X[T] <: Y[T], Y[_]]
        |
        |  trait A[T]
        |  type B[T] <: A[T]
        |
        |  given smth: Z[B, A] = scala.compiletime.deferred
        |""".stripMargin
    )
  }
}
