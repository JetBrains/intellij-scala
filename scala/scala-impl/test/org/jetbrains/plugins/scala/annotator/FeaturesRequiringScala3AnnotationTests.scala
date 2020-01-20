package org.jetbrains.plugins.scala.annotator

import org.jetbrains.plugins.scala.codeInspection.ScalaAnnotatorQuickFixTestBase

abstract class RequiresScala3AnnotationTest extends ScalaAnnotatorQuickFixTestBase

class AnnotationAscriptionRequiresScala3AnnotationTest extends RequiresScala3AnnotationTest {

  override protected val description: String = "annotation ascriptions in pattern definitions require Scala 3.0"

  def test1(): Unit = {
    val text =
      s"""val list: List[String] = List("1")
         |val head :: tail : $START@unchecked$END = list
         |""".stripMargin
    checkTextHasError(text)
  }
}

class CaseSyntaxRequiresScala3AnnotationTest extends RequiresScala3AnnotationTest {

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

