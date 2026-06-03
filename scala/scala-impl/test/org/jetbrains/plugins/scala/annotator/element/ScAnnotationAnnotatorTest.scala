package org.jetbrains.plugins.scala.annotator.element

import org.jetbrains.plugins.scala.ScalaBundle
import org.jetbrains.plugins.scala.annotator.Message.Error
import org.jetbrains.plugins.scala.annotator.ScalaHighlightingTestBase
import org.jetbrains.plugins.scala.util.runners.{MultipleScalaVersionsJUnit4Runner, RunWithScalaVersions, TestScalaVersion}
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(classOf[MultipleScalaVersionsJUnit4Runner])
@RunWithScalaVersions(Array(
  TestScalaVersion.Scala_2_13,
  TestScalaVersion.Scala_3_Latest
))
final class ScAnnotationAnnotatorTest extends ScalaHighlightingTestBase {

  @Test
  def testTraitAsAnnotation(): Unit =
    assertErrors(
      """trait Foo
        |@Foo class Bar
        |""".stripMargin,
      Error("Foo", ScalaBundle.message("annotator.error.annotation.type.expected"))
    )

  @Test
  def testClassAsAnnotation(): Unit =
    assertErrors(
      """class Foo
        |@Foo class Bar
        |""".stripMargin,
      Error("Foo", ScalaBundle.message("annotator.error.annotation.type.expected"))
    )

  @Test
  def testCaseClassAsAnnotation(): Unit =
    assertErrors(
      """case class Foo()
        |@Foo class Bar
        |""".stripMargin,
      Error("Foo", ScalaBundle.message("annotator.error.annotation.type.expected"))
    )

  @Test
  def testAnnotationClassAsAnnotation(): Unit =
    assertNoErrors(
      """class Foo extends scala.annotation.StaticAnnotation
        |@Foo class Bar
        |""".stripMargin
    )

  @Test
  def testScalaAnnotationAsAnnotation(): Unit =
    assertNoErrors(
      """@scala.deprecated class Foo
        |""".stripMargin
    )

  @Test
  def testJavaAnnotationAsAnnotation(): Unit =
    assertNoErrors(
      """@java.lang.Deprecated class Foo
        |""".stripMargin
    )
}
