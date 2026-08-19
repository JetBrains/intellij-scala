package org.jetbrains.plugins.scala.annotator

import org.jetbrains.plugins.scala.codeInspection.ScalaAnnotatorQuickFixTestBase
import org.jetbrains.plugins.scala.{LatestScalaVersions, ScalaVersion}

abstract class RequiresScala3AnnotationTest extends ScalaAnnotatorQuickFixTestBase

class AnnotationAscriptionRequiresScala3AnnotationTest extends RequiresScala3AnnotationTest {

  override protected def supportedIn(version: ScalaVersion): Boolean =
    version < LatestScalaVersions.Scala_3_0

  override protected val description: String = "annotation ascriptions in pattern definitions require Scala 3.0"

  def test1(): Unit = {
    val text =
      s"""val list: List[String] = List("1")
         |val head :: tail : $START@unchecked$END = list
         |""".stripMargin
    checkTextHasError(text)
  }
}

/**
 * `case` in `for` pattern bindings is not only supported in Scala 3, but in Scala 2.12.15 / 2.13.7 as well
 * (see `case in pattern bindings` in [[org.jetbrains.plugins.scala.project.ScalaFeatures]]),
 * so the annotation is only expected in older versions.
 */
class CaseSyntaxRequiresScala3AnnotationTest extends RequiresScala3AnnotationTest {

  override protected def supportedIn(version: ScalaVersion): Boolean =
    version <= LatestScalaVersions.Scala_2_11

  override protected val description = "'case' syntax in 'for' pattern bindings requires Scala 3.0"

  def test1(): Unit = {
    val text =
      s"""for {
         |  ${START}case$END (x1, y) <- Seq()
         |} yield (y, x1)""".stripMargin
    checkTextHasError(text)

    testQuickFix(
      text,
      s"""for {
         |  (x1, y) <- Seq()
         |} yield (y, x1)""".stripMargin,
      "Remove 'case'"
    )
  }
}

class CaseSyntaxInForPatternBindingAnnotationTest extends RequiresScala3AnnotationTest {

  override protected def supportedIn(version: ScalaVersion): Boolean =
    version >= LatestScalaVersions.Scala_2_12.withMinor(15)

  override protected val description = "'case' syntax in 'for' pattern bindings requires Scala 3.0"

  def test1(): Unit =
    checkTextHasNoErrors(
      """for {
        |  case (x1, y) <- Seq()
        |} yield (y, x1)""".stripMargin
    )
}
