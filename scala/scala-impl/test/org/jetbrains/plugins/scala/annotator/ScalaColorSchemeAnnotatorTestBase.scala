package org.jetbrains.plugins.scala.annotator

import com.intellij.psi.PsiElement
import org.jetbrains.plugins.scala.base.ScalaLightCodeInsightFixtureTestCase
import org.jetbrains.plugins.scala.extensions.{PsiElementExt, StringExt}
import org.jetbrains.plugins.scala.highlighter.ScalaColorSchemeAnnotator
import org.jetbrains.plugins.scala.lang.psi.api.ScalaFile
import org.jetbrains.plugins.scala.util.runners.{MultipleScalaVersionsRunner, RunWithScalaVersions, TestScalaVersion}
import org.junit.Assert.assertEquals
import org.junit.runner.RunWith

import scala.collection.immutable.ListSet

/**
 * NOTE: This class only tests [[org.jetbrains.plugins.scala.highlighter.ScalaColorSchemeAnnotator]]<br>
 * It doesn't test standard scala syntax highlighting [[org.jetbrains.plugins.scala.highlighter.ScalaSyntaxHighlighter]]<br>
 *
 * Q: Maybe it would make sense to test all in combination?
 * Because users can't see just highlighting from ScalaColorSchemeAnnotator, they see it on top of ScalaSyntaxHighlighter
 */
@RunWith(classOf[MultipleScalaVersionsRunner])
@RunWithScalaVersions(Array(
  TestScalaVersion.Scala_2_10,
  TestScalaVersion.Scala_2_11,
  TestScalaVersion.Scala_2_12,
  TestScalaVersion.Scala_2_13,
  TestScalaVersion.Scala_3_Latest
))
abstract class ScalaColorSchemeAnnotatorTestBase[T] extends ScalaLightCodeInsightFixtureTestCase {

  protected def needToAnnotateElement(element: PsiElement): Boolean

  protected def annotateWithColorSchemeAnnotator(text: String): Seq[Message2] = {
    configureFromFileText("dummy.scala", text.withNormalizedSeparator)

    val scalaFile = getFile.asInstanceOf[ScalaFile]

    val holder = new AnnotatorHolderExtendedMock(scalaFile)

    scalaFile.breadthFirst().foreach { element =>
      if (needToAnnotateElement(element)) {
        ScalaColorSchemeAnnotator.highlightElement(element)(holder)
      }
    }

    holder.annotations.sortBy(_.range.getStartOffset)
  }

  protected def testHasNoAnnotations(
    text: String,
    filterAnnotationItems: T*
  ): Unit = {
    testAnnotations(text, filterAnnotationItems.to(ListSet), "")
  }

  protected def testAnnotations(
    text: String,
    filterAnnotationItem: T,
    expectedAnnotationsText: String
  ): Unit = {
    testAnnotations(text, Set(filterAnnotationItem), expectedAnnotationsText)
  }

  protected def buildAnnotationsTestText(annotations: Seq[Message2]): String

  protected def getFilterByField(annotation: Message2): T

  protected def testAnnotations(
    text: String,
    filterAnnotationItems: Set[T],
    expectedAnnotationsText: String
  ): Unit = {
    testAnnotationsMatchingCondition(
      text,
      a => filterAnnotationItems.contains(getFilterByField(a)),
      expectedAnnotationsText
    )
  }

  protected def testAllAnnotations(
    text: String,
    expectedAnnotationsText: String
  ): Unit = {
    testAnnotationsMatchingCondition(
      text,
      _ => true,
      expectedAnnotationsText
    )
  }

  private def testAnnotationsMatchingCondition(
    text: String,
    filterAnnotation: Message2 => Boolean,
    expectedAnnotationsText: String
  ): Unit = {
    val annotations = annotateWithColorSchemeAnnotator(text)
    val annotationsWithMatchingMessage = annotations.filter(filterAnnotation)
    val actualAnnotationsText = buildAnnotationsTestText(annotationsWithMatchingMessage)

    assertEquals(
      s"Wrong annotations set for a given annotations filter",
      expectedAnnotationsText.trim,
      actualAnnotationsText.trim
    )
  }
}
