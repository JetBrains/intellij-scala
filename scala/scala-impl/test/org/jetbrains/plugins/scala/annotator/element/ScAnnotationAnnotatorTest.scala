package org.jetbrains.plugins.scala.annotator.element

import org.jetbrains.plugins.scala.ScalaBundle
import org.jetbrains.plugins.scala.annotator.Message.Error
import org.jetbrains.plugins.scala.annotator.ScalaHighlightingTestBase
import org.jetbrains.plugins.scala.util.runners.{MultipleScalaVersionsRunner, RunWithScalaVersions, TestScalaVersion}
import org.junit.runner.RunWith

@RunWith(classOf[MultipleScalaVersionsRunner])
@RunWithScalaVersions(Array(
  TestScalaVersion.Scala_2_13,
  TestScalaVersion.Scala_3_Latest,
))
final class ScAnnotationAnnotatorTest extends ScalaHighlightingTestBase {

  def testTraitAsAnnotation(): Unit =
    assertErrors(
      """trait Foo
        |@Foo class Bar
        |""".stripMargin,
      Error("Foo", ScalaBundle.message("annotator.error.annotation.type.expected"))
    )

  def testClassAsAnnotation(): Unit =
    assertErrors(
      """class Foo
        |@Foo class Bar
        |""".stripMargin,
      Error("Foo", ScalaBundle.message("annotator.error.annotation.type.expected"))
    )

  def testCaseClassAsAnnotation(): Unit =
    assertErrors(
      """case class Foo()
        |@Foo class Bar
        |""".stripMargin,
      Error("Foo", ScalaBundle.message("annotator.error.annotation.type.expected"))
    )

  def testAnnotationClassAsAnnotation(): Unit =
    assertNoErrors(
      """class Foo extends scala.annotation.StaticAnnotation
        |@Foo class Bar
        |""".stripMargin
    )

  def testScalaAnnotationAsAnnotation(): Unit =
    assertNoErrors(
      """@scala.deprecated class Foo
        |""".stripMargin
    )

  def testJavaAnnotationAsAnnotation(): Unit =
    assertNoErrors(
      """@java.lang.Deprecated class Foo
        |""".stripMargin
    )
}
