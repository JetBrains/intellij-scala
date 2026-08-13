package org.jetbrains.plugins.scala.annotator.colorScheme

import com.intellij.openapi.editor.colors.TextAttributesKey
import com.intellij.psi.PsiElement
import org.jetbrains.plugins.scala.annotator.Message2
import org.jetbrains.plugins.scala.util.runners.{RunWithScalaVersions, TestScalaVersion}
import org.junit.Test

/**
 * Same as [[ScalaColorSchemeAnnotatorTest_Scala3]], but for givens in the syntax introduced in Scala 3.6,
 * see [[org.jetbrains.plugins.scala.lang.parser.parsing.top.template.NewGivenDef]]
 */
@RunWithScalaVersions(Array(TestScalaVersion.Scala_3_6))
class ScalaColorSchemeAnnotatorTest_Scala3_6 extends ScalaColorSchemeAnnotatorTestBase[TextAttributesKey] {

  import org.jetbrains.plugins.scala.highlighter.DefaultHighlighter._

  override protected def buildAnnotationsTestText(annotations: Seq[Message2]): String =
    annotations.map(_.textWithRangeAndCodeAttribute).mkString("\n")

  override protected def needToAnnotateElement(element: PsiElement): Boolean = true

  override protected def getFilterByField(annotation: Message2): TextAttributesKey = annotation.textAttributesKey

  //SCL-23464
  @Test
  def testSyntheticGivenNames(): Unit = {
    val code =
      """trait Ord[T]:
        |  def compare(x: T, y: T): Int
        |
        |given [T: Ord] => Ord[List[T]]:
        |  def compare(x: List[T], y: List[T]): Int = 0
        |
        |given Ord[String]:
        |  def compare(x: String, y: String): Int = 0
        |
        |object usage {
        |  given_Ord_List[String]
        |  given_Ord_String
        |}
        |""".stripMargin

    testAnnotations(code, GIVEN,
      """Info((208,222),given_Ord_List,Scala Given)
        |Info((233,249),given_Ord_String,Scala Given)
        |""".stripMargin
    )
  }
}
