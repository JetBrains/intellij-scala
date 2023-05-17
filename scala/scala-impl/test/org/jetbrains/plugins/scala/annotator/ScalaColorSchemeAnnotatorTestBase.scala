package org.jetbrains.plugins.scala.annotator

import com.intellij.psi.PsiElement
import org.jetbrains.plugins.scala.base
import org.jetbrains.plugins.scala.base.{ScalaLightCodeInsightFixtureTestCase, SharedTestProjectToken}
import org.jetbrains.plugins.scala.extensions.{PsiElementExt, StringExt}
import org.jetbrains.plugins.scala.highlighter.ScalaColorSchemeAnnotator
import org.jetbrains.plugins.scala.lang.psi.api.ScalaFile
import org.jetbrains.plugins.scala.util.runners.{MultipleScalaVersionsRunner, RunWithScalaVersions, TestScalaVersion}
import org.junit.Assert.assertEquals
import org.junit.runner.RunWith

import scala.collection.immutable.ListSet

@RunWith(classOf[MultipleScalaVersionsRunner])
@RunWithScalaVersions(Array(
  TestScalaVersion.Scala_2_10,
  TestScalaVersion.Scala_2_11,
  TestScalaVersion.Scala_2_12,
  TestScalaVersion.Scala_2_13,
  TestScalaVersion.Scala_3_Latest
))
abstract class ScalaColorSchemeAnnotatorTestBase[T] extends ScalaLightCodeInsightFixtureTestCase {

  override protected def sharedProjectToken: SharedTestProjectToken =
    base.SharedTestProjectToken(this.getClass)

  protected def needToAnnotateElement(element: PsiElement): Boolean

  protected def annotateWithColorSchemeAnnotator(text: String): AnnotatorHolderExtendedMock = {
    configureFromFileText("dummy.scala", text.withNormalizedSeparator)

    val scalaFile = getFile.asInstanceOf[ScalaFile]

    val holder = new AnnotatorHolderExtendedMock(scalaFile)

    scalaFile.breadthFirst().foreach { element =>
      if (needToAnnotateElement(element)) {
        ScalaColorSchemeAnnotator.highlightElement(element)(holder)
      }
    }

    holder
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
    val holder = annotateWithColorSchemeAnnotator(text)
    val annotationsAll = holder.annotations.sortBy(_.range.getStartOffset)
    val annotationsWithMatchingMessage = annotationsAll.filter { a =>
      filterAnnotationItems.contains(getFilterByField(a))
    }
    val actualAnnotationsText = buildAnnotationsTestText(annotationsWithMatchingMessage)

    assertEquals(
      s"Wrong annotations set for filtered message ${filterAnnotationItems.map(m => s"`$m`").mkString(", ")}",
      expectedAnnotationsText.trim,
      actualAnnotationsText.trim
    )
  }
}